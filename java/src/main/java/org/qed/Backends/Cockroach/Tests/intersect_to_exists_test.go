package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestIntersectToExists(t *testing.T) {
	rule := loadRule(t, "IntersectToExists")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	out := e.f.Metadata().AddColumn("intersect", types.Int)
	setPriv := &memo.SetPrivate{
		LeftCols: opt.ColList{lc[0]}, RightCols: opt.ColList{rc[0]}, OutCols: opt.ColList{out},
	}
	before := e.f.ConstructIntersect(l, r, setPriv)
	on := e.filters(e.f.ConstructIs(e.v(lc[0]), e.v(rc[0])))
	semi := e.f.ConstructSemiJoinApply(l, r, on, &memo.JoinPrivate{})
	grouped := e.f.ConstructGroupBy(semi, memo.AggregationsExpr{}, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(lc[0]),
	})
	want := e.f.ConstructProject(grouped, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(lc[0]), out),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructIntersectAll(l, r, setPriv))
	e.expectNoMatch(rule, e.f.ConstructExcept(l, r, setPriv))
	e.expectNoMatch(rule, e.f.ConstructUnion(l, r, setPriv))
}
