package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestIntersectToSemiJoin(t *testing.T) {
	rule := loadRule(t, "IntersectToSemiJoin")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	out := e.f.Metadata().AddColumn("intersect", e.f.Metadata().ColumnMeta(lc[0]).Type)
	before := e.f.ConstructIntersect(l, r, &memo.SetPrivate{
		LeftCols:  opt.ColList{lc[0]},
		RightCols: opt.ColList{rc[0]},
		OutCols:   opt.ColList{out},
	})
	semi := e.f.ConstructSemiJoin(l, r, e.filters(e.f.ConstructIs(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	agg := e.f.ConstructGroupBy(semi, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])})
	want := e.f.ConstructProject(agg, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(lc[0]), out)}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	allOut := e.f.Metadata().AddColumn("intersect_all", e.f.Metadata().ColumnMeta(lc[0]).Type)
	nearMiss := e.f.ConstructIntersectAll(l, r, &memo.SetPrivate{
		LeftCols:  opt.ColList{lc[0]},
		RightCols: opt.ColList{rc[0]},
		OutCols:   opt.ColList{allOut},
	})
	e.expectNoMatch(rule, nearMiss)
}
