package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateDistinctSetLeft(t *testing.T) {
	rule := loadRule(t, "EliminateDistinctSetLeft")
	e := newEnv(t)
	l, lc := e.scan("t", 3)
	_, rc := e.scan("t", 3)
	o1 := e.f.Metadata().AddColumn("o1", types.Int)
	o2 := e.f.Metadata().AddColumn("o2", types.Int)
	o3 := e.f.Metadata().AddColumn("o3", types.Int)
	leftProj := e.f.ConstructProject(l, memo.ProjectionsExpr{}, opt.MakeColSet(lc[0], lc[1], lc[2]))
	set := &memo.SetPrivate{
		LeftCols:  opt.ColList{lc[2], lc[0], lc[1]},
		RightCols: opt.ColList{rc[0], rc[1], rc[2]},
		OutCols:   opt.ColList{o1, o2, o3},
	}
	empty := e.empty(rc[0], rc[1], rc[2])
	before := e.f.ConstructUnion(leftProj, empty, set)
	group := e.f.ConstructGroupBy(leftProj, memo.AggregationsExpr{}, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(lc[0], lc[1], lc[2]),
	})
	want := e.f.ConstructProject(group, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(lc[2]), o1),
		e.f.ConstructProjectionsItem(e.v(lc[0]), o2),
		e.f.ConstructProjectionsItem(e.v(lc[1]), o3),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructUnionAll(leftProj, empty, set))
	e.expectNoMatch(rule, e.f.ConstructUnion(leftProj, e.intValues(rc, []int{1, 2, 3}), set))
}
