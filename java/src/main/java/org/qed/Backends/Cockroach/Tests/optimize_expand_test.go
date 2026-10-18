package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestOptimizeExpand(t *testing.T) {
	e := newEnv(t)
	if _, err := e.cat.ExecuteDDL("CREATE TABLE expand_input (k INT, c1 INT, c2 INT, v INT)"); err != nil {
		t.Fatal(err)
	}
	rule := loadRule(t, "OptimizeExpand")

	build := func(deduplicate bool) (memo.RelExpr, opt.ColumnID, opt.ColumnID) {
		branches := make([]memo.RelExpr, 3)
		branchCols := make([]opt.ColList, 3)
		for i := range branches {
			scan, cols := e.scan("expand_input", 4)
			if deduplicate {
				scan = e.f.ConstructGroupBy(scan, memo.EmptyAggregationsExpr,
					&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols...)})
			}
			cat1 := e.f.Metadata().AddColumn("cat1", types.Int)
			cat2 := e.f.Metadata().AddColumn("cat2", types.Int)
			all := e.f.ConstructPlus(e.v(cols[0]), e.v(cols[1]))
			all = e.f.ConstructPlus(all, e.v(cols[2]))
			all = e.f.ConstructPlus(all, e.v(cols[3]))
			projections := memo.ProjectionsExpr{
				e.f.ConstructProjectionsItem(all, cat1),
				e.f.ConstructProjectionsItem(all, cat2),
			}
			branches[i] = e.f.ConstructProject(scan, projections, opt.MakeColSet(cols[0], cols[3]))
			branchCols[i] = opt.ColList{cols[0], cat1, cat2, cols[3]}
		}
		union := branches[0]
		out1 := branchCols[0]
		for i := 1; i < len(branches); i++ {
			rcols := branchCols[i]
			next := make(opt.ColList, 4)
			for j := range next {
				next[j] = e.f.Metadata().AddColumn("expand", types.Int)
			}
			union = e.f.ConstructUnionAll(union, branches[i], &memo.SetPrivate{
				LeftCols: out1, RightCols: rcols, OutCols: next,
			})
			out1 = next
		}
		unique := e.f.ConstructGroupBy(union, memo.EmptyAggregationsExpr,
			&memo.GroupingPrivate{GroupingCols: union.Relational().OutputCols})
		groupCols := out1
		countsCol := e.f.Metadata().AddColumn("count", types.Int)
		counts := e.f.ConstructGroupBy(unique,
			memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(groupCols[3])), countsCol)},
			&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(groupCols[0], groupCols[1], groupCols[2])})
		outerCol := e.f.Metadata().AddColumn("outer", types.Int)
		root := e.f.ConstructGroupBy(counts,
			memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(countsCol)), outerCol)},
			&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(groupCols[0])})
		return root, groupCols[0], outerCol
	}

	before, _, _ := build(false)
	wantRoot, wantKey, wantAgg := build(true)
	beforeCols := before.Relational().OutputCols.ToList()
	want := e.f.ConstructProject(wantRoot, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(wantKey), beforeCols[0]),
	}, opt.MakeColSet(wantAgg))
	e.expectRewriteUpToColumnNames(rule, before, want)
	nearMiss, _ := e.scan("t", 1)
	e.expectNoMatch(rule, nearMiss)
}
