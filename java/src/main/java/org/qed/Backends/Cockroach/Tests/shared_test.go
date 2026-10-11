package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func testSimplifyAndFalse(t *testing.T, name string, falseFirst bool) {
	rule := loadRule(t, name)
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	pred := e.f.ConstructEq(e.v(cols[0]), e.num(1))
	falseCond := e.f.ConstructFalse()
	before := e.f.ConstructSelect(s, e.filters(pred, falseCond))
	reversed := e.f.ConstructSelect(s, e.filters(falseCond, pred))
	if falseFirst {
		before, reversed = reversed, before
	}
	want := e.f.ConstructSelect(s, e.filters(falseCond))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, reversed)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(pred)))
}

func testComputedProjectFilterPushdown(t *testing.T, name string, leaveOuter bool) {
	rule := loadRule(t, name)
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	fcol := e.f.Metadata().AddColumn("f", types.Int)
	gcol := e.f.Metadata().AddColumn("g", types.Int)
	fexpr := e.f.ConstructPlus(e.v(cols[0]), e.v(cols[1]))
	gexpr := e.f.ConstructMult(e.v(cols[0]), e.v(cols[1]))
	items := memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(fexpr, fcol),
		e.f.ConstructProjectionsItem(gexpr, gcol),
	}
	project := e.f.ConstructProject(s, items, opt.MakeColSet(cols...))
	p := e.f.ConstructGt(e.v(fcol), e.num(5))
	q := e.f.ConstructLt(e.v(gcol), e.num(10))
	before := e.f.ConstructSelect(project, e.filters(p, q))
	var want memo.RelExpr
	if leaveOuter {
		filtered := e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(fexpr, e.num(5))))
		want = e.f.ConstructSelect(e.f.ConstructProject(filtered, items, opt.MakeColSet(cols...)), e.filters(q))
	} else {
		filtered := e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(fexpr, e.num(5)), e.f.ConstructLt(gexpr, e.num(10))))
		want = e.f.ConstructProject(filtered, items, opt.MakeColSet(cols...))
	}
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(project, e.filters(q, p)))
	e.expectNoMatch(rule, e.f.ConstructSelect(project, e.filters(e.f.ConstructGt(e.v(gcol), e.num(5)), q)))
}

func testAndTrue(t *testing.T, name string, trueFirst bool) {
	rule := loadRule(t, name)
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	pred := e.f.ConstructGt(e.v(cols[0]), e.num(0))
	truth := e.f.ConstructTrue()
	beforeAnd := e.f.ConstructAnd(pred, truth)
	reverseAnd := e.f.ConstructAnd(truth, pred)
	if trueFirst {
		beforeAnd, reverseAnd = reverseAnd, beforeAnd
	}
	before := e.f.ConstructSelect(s, e.filters(beforeAnd))
	want := e.f.ConstructSelect(s, e.filters(pred))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(reverseAnd)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(pred)))
}

func testCommuteInequality(t *testing.T, name string, orEqual bool) {
	rule := loadRule(t, name)
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	join := e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{})
	var before, want memo.RelExpr
	if orEqual {
		before = e.f.ConstructSelect(join, e.filters(e.f.ConstructLe(e.v(lc[0]), e.v(rc[0]))))
		want = e.f.ConstructSelect(join, e.filters(e.f.ConstructGe(e.v(rc[0]), e.v(lc[0]))))
		e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(e.f.ConstructLt(e.v(lc[0]), e.v(rc[0])))))
	} else {
		before = e.f.ConstructSelect(join, e.filters(e.f.ConstructLt(e.v(lc[0]), e.v(rc[0]))))
		want = e.f.ConstructSelect(join, e.filters(e.f.ConstructGt(e.v(rc[0]), e.v(lc[0]))))
		e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(e.f.ConstructLe(e.v(lc[0]), e.v(rc[0])))))
	}
	e.expectRewrite(rule, before, want)
	withOn := e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	if orEqual {
		e.expectNoMatch(rule, e.f.ConstructSelect(withOn, e.filters(e.f.ConstructLe(e.v(lc[0]), e.v(rc[0])))))
	} else {
		e.expectNoMatch(rule, e.f.ConstructSelect(withOn, e.filters(e.f.ConstructLt(e.v(lc[0]), e.v(rc[0])))))
	}
}

func (e *env) intValues(cols []opt.ColumnID, rows ...[]int) memo.RelExpr {
	values := make(memo.ScalarListExpr, len(rows))
	rowTypes := make([]*types.T, len(cols))
	for i := range rowTypes {
		rowTypes[i] = types.Int
	}
	for i, row := range rows {
		elems := make(memo.ScalarListExpr, len(row))
		for j, n := range row {
			elems[j] = e.num(n)
		}
		values[i] = e.f.ConstructTuple(elems, types.MakeTuple(rowTypes))
	}
	return e.f.ConstructValues(values, &memo.ValuesPrivate{
		Cols: append(opt.ColList{}, cols...), ID: e.f.Metadata().NextUniqueID(),
	})
}

func testTryRemapJoinOuterCols(t *testing.T, name string, fromLeft bool) {
	rule := loadRule(t, name)
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	eq := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	join := e.f.ConstructInnerJoinApply(l, r, eq, &memo.JoinPrivate{})
	fromCol, toCol := rc[0], lc[0]
	if fromLeft {
		fromCol, toCol = lc[0], rc[0]
	}
	from := e.f.ConstructSelect(join, e.filters(e.f.ConstructGt(e.v(fromCol), e.num(0))))
	want := e.f.ConstructSelect(join, e.filters(e.f.ConstructGt(e.v(toCol), e.num(0))))
	e.expectRewrite(rule, from, want)
	plain := e.f.ConstructInnerJoin(l, r, eq, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(plain, e.filters(e.f.ConstructGt(e.v(fromCol), e.num(0)))))
	wrong := e.f.ConstructInnerJoinApply(l, r,
		e.filters(e.f.ConstructNe(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(wrong, e.filters(e.f.ConstructGt(e.v(fromCol), e.num(0)))))
}

func testAntiJoinEmpty(t *testing.T, name string) {
	rule := loadRule(t, name)
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	rc := e.f.Metadata().AddColumn("empty", types.Int)
	r := e.empty(rc)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc)))
	before := e.f.ConstructAntiJoin(l, r, on, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, l)

	rScan, _ := e.scan("u", 1)
	e.expectNoMatch(rule, e.f.ConstructAntiJoin(l, rScan, nil, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(l, r, on, &memo.JoinPrivate{}))
	rc2 := e.f.Metadata().AddColumn("extra", types.Int)
	e.expectNoMatch(rule, e.f.ConstructAntiJoin(l, e.empty(rc, rc2), on, &memo.JoinPrivate{}))
}

func testEliminateDistinctSet(t *testing.T, name string) {
	rule := loadRule(t, name)
	e := newEnv(t)
	s, cols := e.scan("t", 3)
	left := e.f.ConstructProject(s, nil, opt.MakeColSet(cols...))
	rightCols := opt.ColList{
		e.f.Metadata().AddColumn("e0", types.Int),
		e.f.Metadata().AddColumn("e1", types.Int),
		e.f.Metadata().AddColumn("e2", types.Int),
	}
	right := e.empty(rightCols...)
	outCols := opt.ColList{
		e.f.Metadata().AddColumn("o0", types.Int),
		e.f.Metadata().AddColumn("o1", types.Int),
		e.f.Metadata().AddColumn("o2", types.Int),
	}
	private := &memo.SetPrivate{
		LeftCols: opt.ColList{cols[2], cols[0], cols[1]}, RightCols: rightCols, OutCols: outCols,
	}
	before := e.f.ConstructUnion(left, right, private)
	grouped := e.f.ConstructGroupBy(left, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(cols...),
	})
	want := e.f.ConstructProject(grouped, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(cols[2]), outCols[0]),
		e.f.ConstructProjectionsItem(e.v(cols[0]), outCols[1]),
		e.f.ConstructProjectionsItem(e.v(cols[1]), outCols[2]),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructUnionAll(left, right, private))
	other, otherCols := e.scan("t", 3)
	e.expectNoMatch(rule, e.f.ConstructUnion(left, other, &memo.SetPrivate{
		LeftCols: private.LeftCols, RightCols: opt.ColList(otherCols), OutCols: outCols,
	}))
}

func testEliminateJoinNoCols(t *testing.T, name string, zeroLeft bool) {
	rule := loadRule(t, name)
	e := newEnv(t)
	rowCol := e.f.Metadata().AddColumn("flag", types.Bool)
	values := func(flag opt.ScalarExpr) memo.RelExpr {
		return e.f.ConstructValues(memo.ScalarListExpr{
			e.f.ConstructTuple(memo.ScalarListExpr{flag}, types.MakeTuple([]*types.T{types.Bool})),
		}, &memo.ValuesPrivate{Cols: opt.ColList{rowCol}, ID: e.f.Metadata().NextUniqueID()})
	}
	zero := e.f.ConstructProject(values(e.f.ConstructTrue()), nil, opt.ColSet{})
	s, cols := e.scan("u", 1)
	on := e.filters(e.f.ConstructEq(e.v(cols[0]), e.num(1)))
	buildJoin := func(zero memo.RelExpr, left bool, outer bool) memo.RelExpr {
		l, r := s, zero
		if left {
			l, r = zero, s
		}
		if outer {
			return e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{})
		}
		return e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	}
	e.expectRewrite(rule, buildJoin(zero, zeroLeft, false), e.f.ConstructSelect(s, on))
	e.expectNoMatch(rule, buildJoin(zero, zeroLeft, true))
	wrong := e.f.ConstructProject(values(e.f.ConstructFalse()), nil, opt.ColSet{})
	e.expectNoMatch(rule, buildJoin(wrong, zeroLeft, false))
}

func testEliminateSetEmptyRight(t *testing.T, name string) {
	rule := loadRule(t, name)
	e := newEnv(t)
	l, lc := e.scan("t", 3)
	lProject := e.f.ConstructProject(l, nil, opt.MakeColSet(lc...))
	rc := opt.ColList{
		e.f.Metadata().AddColumn("r2", types.Int),
		e.f.Metadata().AddColumn("r0", types.Int),
		e.f.Metadata().AddColumn("r1", types.Int),
	}
	r := e.empty(rc...)
	oc := opt.ColList{
		e.f.Metadata().AddColumn("o0", types.Int),
		e.f.Metadata().AddColumn("o1", types.Int),
		e.f.Metadata().AddColumn("o2", types.Int),
	}
	private := &memo.SetPrivate{
		LeftCols:  opt.ColList{lc[2], lc[0], lc[1]},
		RightCols: rc,
		OutCols:   oc,
	}
	before := e.f.ConstructUnionAll(lProject, r, private)
	want := e.f.ConstructProject(lProject, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(lc[2]), oc[0]),
		e.f.ConstructProjectionsItem(e.v(lc[0]), oc[1]),
		e.f.ConstructProjectionsItem(e.v(lc[1]), oc[2]),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructUnion(lProject, r, private))
	rScan, rCols := e.scan("t", 3)
	e.expectNoMatch(rule, e.f.ConstructUnionAll(lProject, rScan, &memo.SetPrivate{
		LeftCols: private.LeftCols, RightCols: opt.ColList{rCols[0], rCols[1], rCols[2]}, OutCols: oc,
	}))
	e.expectNoMatch(rule, e.f.ConstructUnionAll(l, r, &memo.SetPrivate{
		LeftCols: opt.ColList{lc[2], lc[0], lc[1]}, RightCols: rc, OutCols: oc,
	}))
}

func (e *env) exists(input memo.RelExpr) opt.ScalarExpr {
	col := e.f.Metadata().AddColumn("exists", types.Bool)
	return e.f.ConstructExists(input, &memo.ExistsPrivate{LazyEvalProjectionCol: col})
}

func testHoistSelectExists(t *testing.T, name string, negated bool) {
	e := newEnv(t)
	rule := loadRule(t, name)
	outer, oc := e.scan("u", 1)
	inner, ic := e.scan("t", 1)
	subFilter := e.f.ConstructGt(e.v(ic[0]), e.num(0))
	rest := e.f.ConstructGt(e.v(oc[0]), e.num(0))
	filtered := e.f.ConstructSelect(inner, e.filters(subFilter))
	var first opt.ScalarExpr
	if negated {
		first = e.f.ConstructNot(e.exists(filtered))
	} else {
		first = e.exists(filtered)
	}
	before := e.f.ConstructSelect(outer, e.filters(first, rest))
	var joined memo.RelExpr
	if negated {
		joined = e.f.ConstructAntiJoin(outer, filtered, nil, &memo.JoinPrivate{})
	} else {
		joined = e.f.ConstructSemiJoin(outer, filtered, nil, &memo.JoinPrivate{})
	}
	want := e.f.ConstructSelect(joined, e.filters(rest))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(rest, first)))
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(first, rest,
		e.f.ConstructIsNot(e.v(oc[0]), memo.NullSingleton))))
}

func testSingleElementMembership(t *testing.T, name string, negated bool) {
	rule := loadRule(t, name)
	e := newEnv(t)
	s, cols := e.scan("t", 2)
	left, right := e.v(cols[0]), e.v(cols[1])
	tuple := e.f.ConstructTuple(memo.ScalarListExpr{right}, types.MakeTuple([]*types.T{right.DataType()}))
	var beforeCond opt.ScalarExpr = e.f.ConstructIn(left, tuple)
	var wantCond opt.ScalarExpr = e.f.ConstructEq(left, right)
	if negated {
		beforeCond = e.f.ConstructNotIn(left, tuple)
		wantCond = e.f.ConstructNe(left, right)
	}
	e.expectRewrite(rule, e.f.ConstructSelect(s, e.filters(beforeCond)), e.f.ConstructSelect(s, e.filters(wantCond)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(wantCond)))
	two := e.f.ConstructTuple(memo.ScalarListExpr{right, right}, types.MakeTuple([]*types.T{right.DataType(), right.DataType()}))
	if negated {
		e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructNotIn(left, two))))
	} else {
		e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructIn(left, two))))
	}
}

func testInlineSelectEqualColumns(t *testing.T, name string) {
	rule := loadRule(t, name)
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	join := e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	before := e.f.ConstructSelect(join, e.filters(e.f.ConstructEq(e.v(lc[0]), e.num(1))))
	want := e.f.ConstructSelect(join, e.filters(e.f.ConstructEq(e.v(rc[0]), e.num(1))))
	e.expectRewrite(rule, before, want)

	wrongJoin := e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructNe(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(wrongJoin, e.filters(e.f.ConstructEq(e.v(lc[0]), e.num(1)))))
	e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(e.f.ConstructEq(e.v(rc[0]), e.num(1)))))
}

func twoNonNullColumns(e *env) (memo.RelExpr, []opt.ColumnID) {
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	return e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{}), []opt.ColumnID{lc[0], rc[0]}
}

func testPushFilterIntoJoinSide(t *testing.T, name string, pushRight bool) {
	rule := loadRule(t, name)
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	unbound := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	bound := e.f.ConstructGt(e.v(lc[0]), e.num(1))
	if pushRight {
		bound = e.f.ConstructGt(e.v(rc[0]), e.num(1))
	}
	before := e.f.ConstructInnerJoin(l, r, e.filters(bound, unbound), &memo.JoinPrivate{})
	var want memo.RelExpr
	if pushRight {
		filtered := e.f.ConstructSelect(r, e.filters(bound))
		want = e.f.ConstructInnerJoin(l, filtered, e.filters(unbound), &memo.JoinPrivate{})
	} else {
		filtered := e.f.ConstructSelect(l, e.filters(bound))
		want = e.f.ConstructInnerJoin(filtered, r, e.filters(unbound), &memo.JoinPrivate{})
	}
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(unbound, bound), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, e.filters(bound, unbound), &memo.JoinPrivate{}))
}

func testAssociateJoins(t *testing.T, name string) {
	rule := loadRule(t, name)
	e := newEnv(t)
	a, ac := e.scan("u", 1)
	b, bc := e.scan("t", 1)
	c, cc := e.scan("nn", 1)
	ab := e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(bc[0])))
	acCond := e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(cc[0])))
	bcCond := e.filters(e.f.ConstructEq(e.v(bc[0]), e.v(cc[0])))
	all := opt.MakeColSet(ac[0], bc[0], cc[0])
	join := func(left, right memo.RelExpr, on memo.FiltersExpr) memo.RelExpr {
		return e.f.ConstructInnerJoin(left, right, on, &memo.JoinPrivate{})
	}
	leftJoin := func(left, right memo.RelExpr, on memo.FiltersExpr) memo.RelExpr {
		return e.f.ConstructLeftJoin(left, right, on, &memo.JoinPrivate{})
	}
	project := func(input memo.RelExpr) memo.RelExpr { return e.f.ConstructProject(input, nil, all) }
	concat := func(left, right memo.FiltersExpr) memo.FiltersExpr {
		return append(append(memo.FiltersExpr{}, left...), right...)
	}
	var before, want, wrongInner memo.RelExpr
	switch name {
	case "AssociateLimitJoinsLeft":
		inner := leftJoin(a, b, ab)
		before = join(inner, c, acCond)
		want = project(leftJoin(join(a, c, acCond), b, ab))
		wrongInner = join(join(a, b, ab), c, acCond)
	case "AssociateLimitJoinsRight":
		inner := leftJoin(a, b, ab)
		before = join(c, inner, acCond)
		want = project(leftJoin(join(a, c, acCond), b, ab))
		wrongInner = join(c, join(a, b, ab), acCond)
	case "LeftAssociateJoinsLeft":
		inner := join(a, b, nil)
		before = join(inner, c, concat(acCond, bcCond))
		want = project(join(b, join(a, c, acCond), bcCond))
		wrongInner = join(leftJoin(a, b, nil), c, concat(acCond, bcCond))
	case "LeftAssociateJoinsRight":
		inner := join(a, b, nil)
		before = join(inner, c, concat(acCond, bcCond))
		want = join(a, join(b, c, bcCond), acCond)
		wrongInner = join(leftJoin(a, b, nil), c, concat(acCond, bcCond))
	case "RightAssociateJoinsLeft":
		inner := join(b, c, nil)
		before = join(a, inner, concat(ab, bcCond))
		want = join(join(a, b, ab), c, bcCond)
		wrongInner = join(a, leftJoin(b, c, nil), concat(ab, bcCond))
	case "RightAssociateJoinsRight":
		inner := join(b, c, nil)
		before = join(a, inner, concat(acCond, ab))
		want = project(join(join(a, c, acCond), b, ab))
		wrongInner = join(a, leftJoin(b, c, nil), concat(acCond, ab))
	default:
		t.Fatalf("unknown association shape %s", name)
	}
	e.expectRewrite(rule, before, want)
	root := before.(*memo.InnerJoinExpr)
	e.expectNoMatch(rule, leftJoin(root.Left, root.Right, root.On))
	e.expectNoMatch(rule, wrongInner)
}

func testFoldNonNullScalar(t *testing.T, name string, notNull bool) {
	rule := loadRule(t, name)
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	var condition opt.ScalarExpr = e.f.ConstructIs(e.v(cols[0]), memo.NullSingleton)
	var other opt.ScalarExpr = e.f.ConstructIsNot(e.v(cols[0]), memo.NullSingleton)
	var want memo.RelExpr = e.f.ConstructSelect(s, e.filters(e.f.ConstructFalse()))
	if notNull {
		condition, other = other, condition
		want = s
	}
	e.expectRewrite(rule, e.f.ConstructSelect(s, e.filters(condition)), want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(other)))
	s2, c2 := e.scan("t", 2)
	e.expectNoMatch(rule, e.f.ConstructSelect(s2, e.filters(e.f.ConstructIs(e.v(c2[1]), memo.NullSingleton))))
}

func testRemoveNullCondition(t *testing.T, name string, notNull bool) {
	rule := loadRule(t, name)
	e := newEnv(t)
	s, cols := e.scan("t", 2)
	inner := e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(cols[1]), e.num(0))))
	var condition opt.ScalarExpr = e.f.ConstructIs(e.v(cols[0]), memo.NullSingleton)
	var want memo.RelExpr = e.f.ConstructSelect(s, e.filters(e.f.ConstructFalse()))
	if notNull {
		condition = e.f.ConstructIsNot(e.v(cols[0]), memo.NullSingleton)
		want = inner
	}
	e.expectRewrite(rule, e.f.ConstructSelect(inner, e.filters(condition)), want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(condition)))
	var wrongCol opt.ScalarExpr = e.f.ConstructIs(e.v(cols[1]), memo.NullSingleton)
	if notNull {
		wrongCol = e.f.ConstructIsNot(e.v(cols[1]), memo.NullSingleton)
	}
	e.expectNoMatch(rule, e.f.ConstructSelect(inner, e.filters(wrongCol)))
}

func testRejectNullsOuterJoin(t *testing.T, name string, full bool) {
	rule := loadRule(t, name)
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	var beforeJoin memo.RelExpr = e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{})
	var afterJoin memo.RelExpr = e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	var reject opt.ScalarExpr = e.f.ConstructIsNot(e.v(rc[0]), memo.NullSingleton)
	var wrong opt.ScalarExpr = e.f.ConstructIsNot(e.v(lc[0]), memo.NullSingleton)
	if full {
		beforeJoin = e.f.ConstructFullJoin(l, r, on, &memo.JoinPrivate{})
		afterJoin = e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{})
		reject, wrong = wrong, reject
	}
	e.expectRewrite(rule, e.f.ConstructSelect(beforeJoin, e.filters(reject)), e.f.ConstructSelect(afterJoin, e.filters(reject)))
	e.expectNoMatch(rule, e.f.ConstructSelect(beforeJoin, e.filters(wrong)))
	e.expectNoMatch(rule, e.f.ConstructSelect(afterJoin, e.filters(reject)))
}

func testRejectNullsUnderJoin(t *testing.T, name string, rightSide bool) {
	rule := loadRule(t, name)
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	col := lc[0]
	otherCol := rc[0]
	if rightSide {
		col, otherCol = otherCol, col
	}
	reject := e.f.ConstructIsNot(e.v(col), memo.NullSingleton)
	wrong := e.f.ConstructIsNot(e.v(otherCol), memo.NullSingleton)
	before := e.f.ConstructInnerJoin(l, r, e.filters(on, reject), &memo.JoinPrivate{})
	var want memo.RelExpr
	if rightSide {
		want = e.f.ConstructInnerJoin(l, e.f.ConstructSelect(r, e.filters(reject)), e.filters(on, reject), &memo.JoinPrivate{})
	} else {
		want = e.f.ConstructInnerJoin(e.f.ConstructSelect(l, e.filters(reject)), r, e.filters(on, reject), &memo.JoinPrivate{})
	}
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(reject, on), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(on, wrong), &memo.JoinPrivate{}))
}

func testFoldIsNullLiteral(t *testing.T, name string, negated bool) {
	rule := loadRule(t, name)
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	nullInt := e.f.ConstructNull(types.Int)
	var condition opt.ScalarExpr = e.f.ConstructIs(nullInt, memo.NullSingleton)
	var wrong opt.ScalarExpr = e.f.ConstructIsNot(nullInt, memo.NullSingleton)
	var want memo.RelExpr = s
	if negated {
		condition, wrong = wrong, condition
		want = e.f.ConstructSelect(s, e.filters(e.f.ConstructFalse()))
	}
	e.expectRewrite(rule, e.f.ConstructSelect(s, e.filters(condition)), want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(wrong)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructIs(e.v(cols[0]), memo.NullSingleton))))
}

func testSimplifySameVar(t *testing.T, name string, inequality bool) {
	rule := loadRule(t, name)
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	x := e.v(cols[0])
	y := e.v(cols[1])
	nullBool := e.f.ConstructNull(types.Bool)
	var condition opt.ScalarExpr = e.f.ConstructEq(x, x)
	var want memo.RelExpr = e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(e.f.ConstructIsNot(x, memo.NullSingleton), nullBool)))
	var wrong opt.ScalarExpr = e.f.ConstructEq(x, y)
	var other opt.ScalarExpr = e.f.ConstructNe(x, x)
	if inequality {
		condition, other = other, condition
		wrong = e.f.ConstructNe(x, y)
		want = e.f.ConstructSelect(s, e.filters(e.f.ConstructIs(x, memo.NullSingleton), nullBool))
	}
	e.expectRewrite(rule, e.f.ConstructSelect(s, e.filters(condition)), want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(wrong)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(other)))
}

func testSimplifyOrLiteral(t *testing.T, name string, literalTrue, literalFirst bool) {
	rule := loadRule(t, name)
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	pred := e.f.ConstructEq(e.v(cols[0]), e.num(1))
	literal := opt.ScalarExpr(e.f.ConstructFalse())
	opposite := opt.ScalarExpr(e.f.ConstructTrue())
	if literalTrue {
		literal, opposite = opposite, literal
	}
	left, right := opt.ScalarExpr(pred), literal
	if literalFirst {
		left, right = literal, pred
	}
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(left, right)))
	var want = s
	if !literalTrue {
		want = e.f.ConstructSelect(s, e.filters(pred))
	}
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(right, left))))
	if literalFirst {
		e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(opposite, pred))))
	} else {
		e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(pred, opposite))))
	}
}

func testPruneOneInputCol(t *testing.T, name string) {
	rule := loadRule(t, name)
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	out := e.f.Metadata().AddColumn("out", types.Int)
	before := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(1)), out),
	}, opt.ColSet{})
	pruned := e.f.ConstructProject(s, nil, opt.MakeColSet(cols[0]))
	want := e.f.ConstructProject(pruned, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(1)), out),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[1]), e.num(1)), out),
	}, opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(1)), out),
	}, opt.MakeColSet(cols[1])))
	e.expectNoMatch(rule, s)
}

func testPruneJoinLeftCols(t *testing.T, name string) {
	rule := loadRule(t, name)
	e := newEnv(t)
	l, lc := e.scan("u", 2)
	r, rc := e.scan("t", 1)
	out := e.f.Metadata().AddColumn("sum", types.Int)
	on := func() memo.FiltersExpr { return e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))) }
	join := e.f.ConstructInnerJoin(l, r, on(), &memo.JoinPrivate{})
	before := e.f.ConstructProject(join, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(lc[0]), e.v(rc[0])), out),
	}, opt.ColSet{})
	lpruned := e.f.ConstructProject(l, nil, opt.MakeColSet(lc[0]))
	wantJoin := e.f.ConstructInnerJoin(lpruned, r, on(), &memo.JoinPrivate{})
	want := e.f.ConstructProject(wantJoin, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(lc[0]), e.v(rc[0])), out),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	wrongOn := e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructEq(e.v(lc[1]), e.v(rc[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructProject(wrongOn, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(lc[0]), e.v(rc[0])), out),
	}, opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructProject(join, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(lc[1]), e.v(rc[0])), out),
	}, opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructProject(e.f.ConstructLeftJoin(l, r, on(), &memo.JoinPrivate{}), memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(lc[0]), e.v(rc[0])), out),
	}, opt.ColSet{}))
}

func testPruneScanCols(t *testing.T, name string) {
	rule := loadRule(t, name)
	e := newEnv(t)
	s, cols := e.scan("t", 3)
	out := e.f.Metadata().AddColumn("sum", types.Int)
	before := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.v(cols[1])), out),
	}, opt.ColSet{})
	pruned := e.f.ConstructProject(s, nil, opt.MakeColSet(cols[0], cols[1]))
	want := e.f.ConstructProject(pruned, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.v(cols[1])), out),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.v(cols[2])), out),
	}, opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.v(cols[1])), out),
	}, opt.MakeColSet(cols[2])))
	e.expectNoMatch(rule, s)
}

func testPushJoinThroughBarrier(t *testing.T, name string, barrierOnLeft bool) {
	rule := loadRule(t, name)
	e := newEnv(t)
	l, lc := e.scan("u", 2)
	r, rc := e.scan("u", 2)
	leftBarrier := e.f.ConstructProject(l, nil, opt.MakeColSet(lc...))
	rightBarrier := e.f.ConstructProject(r, nil, opt.MakeColSet(rc...))
	on := func() memo.FiltersExpr { return e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))) }
	var before memo.RelExpr
	if barrierOnLeft {
		before = e.f.ConstructInnerJoin(leftBarrier, r, on(), &memo.JoinPrivate{})
	} else {
		before = e.f.ConstructInnerJoin(l, rightBarrier, on(), &memo.JoinPrivate{})
	}
	inner := e.f.ConstructInnerJoin(l, r, on(), &memo.JoinPrivate{})
	want := e.f.ConstructProject(inner, nil, opt.MakeColSet(lc[0], lc[1], rc[0], rc[1]))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, on(), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(leftBarrier, r, on(), &memo.JoinPrivate{}))
	if barrierOnLeft {
		e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, rightBarrier, on(), &memo.JoinPrivate{}))
	} else {
		e.expectNoMatch(rule, e.f.ConstructInnerJoin(leftBarrier, r, on(), &memo.JoinPrivate{}))
	}
}

func testSimplifySelfJoin(t *testing.T, name, side string) {
	rule := loadRule(t, name)
	e := newEnv(t)
	l, lc := e.scan("nn", 1)
	r, rc := e.scan("nn", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	var before memo.RelExpr
	if side == "left" {
		before = e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{})
	} else {
		before = e.f.ConstructFullJoin(l, r, on, &memo.JoinPrivate{})
	}
	var want memo.RelExpr = e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	if side == "right" {
		want = e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{})
	}
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, want)
	u, uc := e.scan("u", 1)
	different := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(uc[0])))
	if side == "left" {
		e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, u, different, &memo.JoinPrivate{}))
	} else {
		e.expectNoMatch(rule, e.f.ConstructFullJoin(l, u, different, &memo.JoinPrivate{}))
	}
}

func testEliminateSelfJoinProject(t *testing.T, name string, keepRight bool) {
	rule := loadRule(t, name)
	e := newEnv(t)
	l, lc := e.scan("nn", 1)
	r, rc := e.scan("nn", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	var join memo.RelExpr = e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	if !keepRight {
		join = e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{})
	}
	keep, chosen := l, lc[0]
	wrong := rc[0]
	if keepRight {
		keep, chosen, wrong = r, rc[0], lc[0]
	}
	before := e.f.ConstructProject(join, nil, opt.MakeColSet(chosen))
	want := e.f.ConstructProject(keep, nil, opt.MakeColSet(chosen))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructProject(join, nil, opt.MakeColSet(wrong)))
	e.expectNoMatch(rule, e.f.ConstructProject(join, nil, opt.MakeColSet(lc[0], rc[0])))
}

func testBooleanComparisonFold(t *testing.T, name string, unequal, literalTrue bool) {
	rule := loadRule(t, name)
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	pred := e.f.ConstructEq(e.v(cols[0]), e.num(1))
	literal := opt.ScalarExpr(e.f.ConstructFalse())
	other := opt.ScalarExpr(e.f.ConstructTrue())
	if literalTrue {
		literal, other = other, literal
	}
	var comparison, otherComparison opt.ScalarExpr
	if unequal {
		comparison = e.f.ConstructNe(pred, literal)
		otherComparison = e.f.ConstructEq(pred, literal)
	} else {
		comparison = e.f.ConstructEq(pred, literal)
		otherComparison = e.f.ConstructNe(pred, literal)
	}
	before := e.f.ConstructSelect(s, e.filters(comparison))
	result := pred
	if unequal == literalTrue {
		result = e.f.ConstructNot(pred)
	}
	want := e.f.ConstructSelect(s, e.filters(result))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(otherComparison)))
	if unequal {
		e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructNe(literal, pred))))
	} else {
		e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(literal, pred))))
	}
}

func testNotLiteralFold(t *testing.T, name string, literalTrue bool) {
	rule := loadRule(t, name)
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	literal := opt.ScalarExpr(e.f.ConstructFalse())
	other := opt.ScalarExpr(e.f.ConstructTrue())
	if literalTrue {
		literal, other = other, literal
	}
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructNot(literal)))
	var want = s
	if literalTrue {
		want = e.f.ConstructSelect(s, e.filters(e.f.ConstructFalse()))
	}
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructNot(other))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructNot(e.f.ConstructEq(e.v(cols[0]), e.num(1))))))
}

func oneColumnUnionAll(e *env) (memo.RelExpr, opt.ColumnID) {
	left, lc := e.scan("u", 1)
	right, rc := e.scan("t", 1)
	out := e.f.Metadata().AddColumn("union", types.Int)
	return e.f.ConstructUnionAll(left, right, &memo.SetPrivate{
		LeftCols: opt.ColList{lc[0]}, RightCols: opt.ColList{rc[0]}, OutCols: opt.ColList{out},
	}), out
}

func testEliminateKeyedGrouping(t *testing.T, name string) {
	rule := loadRule(t, name)
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	grouping := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0])}
	before := e.f.ConstructGroupBy(s, nil, grouping)
	want := e.f.ConstructProject(s, nil, opt.MakeColSet(cols[0]))
	e.expectRewrite(rule, before, want)

	nonUnique, col := oneColumnUnionAll(e)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(nonUnique, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(col),
	}))
	aggCol := e.f.Metadata().AddColumn("count", types.Int)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(s, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(cols[0])), aggCol),
	}, grouping))
}

func testEliminateKeyedAggDistinct(t *testing.T, name string) {
	rule := loadRule(t, name)
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	result := e.f.Metadata().AddColumn("result", types.Int)
	grouping := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0])}
	before := e.f.ConstructGroupBy(s, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructAggDistinct(e.f.ConstructCount(e.v(cols[0]))), result),
	}, grouping)
	want := e.f.ConstructGroupBy(s, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(cols[0])), result),
	}, grouping)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, want)
	nonUnique, col := oneColumnUnionAll(e)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(nonUnique, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructAggDistinct(e.f.ConstructCount(e.v(col))), result),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(col)}))
}

func testSimplifyIntersect(t *testing.T, name string, reverse bool) {
	rule := loadRule(t, name)
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	out := e.f.Metadata().AddColumn("out", types.Int)
	private := &memo.SetPrivate{
		LeftCols: opt.ColList{lc[0]}, RightCols: opt.ColList{rc[0]}, OutCols: opt.ColList{out},
	}
	before := e.f.ConstructIntersect(l, r, private)
	var surviving opt.ColumnID
	var semi memo.RelExpr
	if reverse {
		surviving = rc[0]
		semi = e.f.ConstructSemiJoin(r, l, e.filters(e.f.ConstructIs(e.v(rc[0]), e.v(lc[0]))), &memo.JoinPrivate{})
	} else {
		surviving = lc[0]
		semi = e.f.ConstructSemiJoin(l, r, e.filters(e.f.ConstructIs(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	}
	want := e.f.ConstructProject(semi, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(surviving), out),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructIntersectAll(l, r, private))
	nonUnique, col := oneColumnUnionAll(e)
	if reverse {
		e.expectNoMatch(rule, e.f.ConstructIntersect(l, nonUnique, &memo.SetPrivate{
			LeftCols: private.LeftCols, RightCols: opt.ColList{col}, OutCols: private.OutCols,
		}))
	} else {
		e.expectNoMatch(rule, e.f.ConstructIntersect(nonUnique, r, &memo.SetPrivate{
			LeftCols: opt.ColList{col}, RightCols: private.RightCols, OutCols: private.OutCols,
		}))
	}
}

func expectSQLFires(t *testing.T, rule, sql string) {
	t.Helper()
	plan, fired := planFor(t, sql, nil, rule)
	if len(fired) == 0 {
		t.Fatalf("%s did not fire on %s\n%s", rule, sql, plan)
	}
}

func expectSQLFiresOrNot(t *testing.T, rule, sql string, fires bool) {
	t.Helper()
	plan, fired := planFor(t, sql, nil, rule)
	if (len(fired) > 0) != fires {
		t.Fatalf("%s: unexpected firing on %s: %v\n%s", rule, sql, fired, plan)
	}
}

func expectSQLRewrites(t *testing.T, rule, sql string) {
	t.Helper()
	before, _ := planFor(t, sql, nil)
	got, fired := planFor(t, sql, nil, rule)
	if strings.Join(fired, ",") != rule || got == before {
		t.Fatalf("expected %s to change the SQL plan, got %v:\n%s", rule, fired, got)
	}
}

func expectSQLScanConstraint(t *testing.T, rule, sql, beforeToken, afterMissing string) {
	t.Helper()
	before, _ := planFor(t, sql, nil)
	if !strings.Contains(before, beforeToken) {
		t.Fatalf("expected %s before rewrite:\n%s", beforeToken, before)
	}
	got, fired := planFor(t, sql, nil, rule)
	if strings.Join(fired, ",") != rule ||
		(afterMissing != "" && strings.Contains(got, afterMissing)) {
		t.Fatalf("expected %s to rewrite plan, got %v:\n%s", rule, fired, got)
	}
}

func expectSQLRepeatedScan(t *testing.T, rule, sql, beforeOp, afterOp string, fires bool) {
	t.Helper()
	helper := func(r opt.RuleName) bool {
		return r == opt.EliminateProject || strings.HasPrefix(r.String(), "Prune")
	}
	before, bFired := planFor(t, sql, helper)
	after, fired := planFor(t, sql, helper, rule)
	if len(bFired) != 0 || !strings.Contains(before, beforeOp) || !strings.Contains(after, afterOp) {
		t.Fatalf("unexpected planner shape for %s: before %v:\n%s\nafter %v:\n%s", rule, bFired, before, fired, after)
	}
	if fires {
		if strings.Join(fired, ",") != rule || strings.Contains(after, beforeOp) {
			t.Fatalf("%s did not rewrite SQL plan: %v:\n%s", rule, fired, after)
		}
	} else if len(fired) != 0 || before != after {
		t.Fatalf("%s unexpectedly matched a different SQL shape: %v:\n%s", rule, fired, after)
	}
}
