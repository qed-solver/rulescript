package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestFullToLeftAndRightJoin(t *testing.T) {
	rule := loadRule(t, "FullToLeftAndRightJoin")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	eq := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	on := e.filters(eq)
	before := e.f.ConstructFullJoin(l, r, on, &memo.JoinPrivate{})

	base := e.f.Metadata().MaxColumn()
	fresh := opt.ColList{base + 1, base + 2}

	leftJoin := e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{})
	rightJoin := e.f.ConstructRightJoin(l, r, on, &memo.JoinPrivate{})
	notMatched := e.f.ConstructSelect(rightJoin, e.filters(e.f.ConstructIsNot(eq, e.f.ConstructTrue())))
	renamed := e.f.ConstructProject(notMatched, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(lc[0]), fresh[0]),
		e.f.ConstructProjectionsItem(e.v(rc[0]), fresh[1]),
	}, opt.ColSet{})

	got, ok := rule.Apply(&e.f, before)
	if !ok {
		t.Fatal("FullToLeftAndRightJoin did not apply")
	}
	top, ok := got.(*memo.ProjectExpr)
	if !ok {
		t.Fatalf("expected a project over the union, got %s", e.format(got))
	}
	unionOut := top.Input.Private().(*memo.SetPrivate).OutCols
	union := e.f.ConstructUnionAll(leftJoin, renamed, &memo.SetPrivate{
		LeftCols: opt.ColList{lc[0], rc[0]}, RightCols: fresh, OutCols: unionOut,
	})
	want := e.f.ConstructProject(union, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(unionOut[0]), lc[0]),
		e.f.ConstructProjectionsItem(e.v(unionOut[1]), rc[0]),
	}, opt.ColSet{})
	if g, w := e.format(got), e.format(want); g != w {
		t.Fatalf("FullToLeftAndRightJoin produced an unexpected plan\ninput:\n%s\ngot:\n%s\nwant:\n%s",
			e.format(before), g, w)
	}

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructFullJoin(l, r,
		e.filters(e.f.ConstructNe(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructFullJoin(l, r, e.filters(e.f.ConstructTrue()), &memo.JoinPrivate{}))
}
