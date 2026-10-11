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
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	out := e.f.Metadata().AddColumn("out", types.Int)
	private := &memo.SetPrivate{
		LeftCols:  opt.ColList{lc[0]},
		RightCols: opt.ColList{rc[0]},
		OutCols:   opt.ColList{out},
	}
	before := e.f.ConstructIntersect(l, r, private)
	exists := e.f.ConstructSemiJoinApply(l, r,
		e.filters(e.f.ConstructIs(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	want := e.f.ConstructProject(
		e.f.ConstructGroupBy(exists, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])}),
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(lc[0]), out)},
		opt.ColSet{},
	)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructIntersectAll(l, r, private))
	e.expectNoMatch(rule, e.f.ConstructUnion(l, r, private))
	e.expectNoMatch(rule, e.f.ConstructSelect(before, e.filters(e.f.ConstructGt(e.v(out), e.num(0)))))
}
