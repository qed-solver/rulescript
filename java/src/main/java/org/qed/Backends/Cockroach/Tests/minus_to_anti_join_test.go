package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestMinusToAntiJoin(t *testing.T) {
	rule := loadRule(t, "MinusToAntiJoin")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	out := e.f.Metadata().AddColumn("out", types.Int)
	setPrivate := func() *memo.SetPrivate {
		return &memo.SetPrivate{LeftCols: opt.ColList{lc[0]}, RightCols: opt.ColList{rc[0]}, OutCols: opt.ColList{out}}
	}

	before := e.f.ConstructExcept(l, r, setPrivate())
	on := e.filters(e.f.ConstructIs(e.v(lc[0]), e.v(rc[0])))
	want := func(input memo.RelExpr) memo.RelExpr {
		return e.f.ConstructProject(
			e.f.ConstructGroupBy(
				e.f.ConstructAntiJoin(input, r, on, &memo.JoinPrivate{}),
				nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])},
			),
			memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(lc[0]), out)},
			opt.ColSet{},
		)
	}
	e.expectRewrite(rule, before, want(l))

	e.expectNoMatch(rule, e.f.ConstructExceptAll(l, r, setPrivate()))
	e.expectNoMatch(rule, e.f.ConstructUnion(l, r, setPrivate()))
	sel := e.f.ConstructSelect(l, e.filters(e.f.ConstructGt(e.v(lc[0]), e.num(1))))
	e.expectRewrite(rule, e.f.ConstructExcept(sel, r, setPrivate()), want(sel))
}
