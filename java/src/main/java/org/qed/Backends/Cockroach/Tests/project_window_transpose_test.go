package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestProjectWindowTranspose(t *testing.T) {
	rule := loadRule(t, "ProjectWindowTranspose")
	e := newEnv(t)
	input, ic := e.scan("t", 3)
	partitions, pc := e.scan("t", 3)
	needed := e.f.ConstructProject(partitions, nil, opt.MakeColSet(pc[0], pc[1]))
	f := e.f.Metadata().AddColumn("f", types.Int)
	g := e.f.Metadata().AddColumn("g", types.Int)
	perPartition := e.f.ConstructGroupBy(needed, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(pc[1])), f),
		e.f.ConstructAggregationsItem(e.f.ConstructMin(e.v(pc[1])), g),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(pc[0])})
	z := e.f.Metadata().AddColumn("top", types.Int)
	top := func(cols ...opt.ColumnID) memo.ProjectionsExpr {
		sum := e.f.ConstructPlus(
			e.f.ConstructPlus(e.v(cols[0]), e.v(cols[1])), e.f.ConstructPlus(e.v(cols[2]), e.v(cols[3])))
		return memo.ProjectionsExpr{e.f.ConstructProjectionsItem(sum, z)}
	}
	full := e.f.ConstructInnerJoin(input, perPartition,
		e.filters(e.f.ConstructEq(e.v(ic[0]), e.v(pc[0]))), &memo.JoinPrivate{})
	before := e.f.ConstructProject(full, top(ic[0], ic[1], f, g), opt.ColSet{})
	pruned := e.f.ConstructProject(input, nil, opt.MakeColSet(ic[0], ic[1]))
	prunedJoin := e.f.ConstructInnerJoin(pruned, perPartition,
		e.filters(e.f.ConstructEq(e.v(ic[0]), e.v(pc[0]))), &memo.JoinPrivate{})
	want := e.f.ConstructProject(prunedJoin, top(ic[0], ic[1], f, g), opt.ColSet{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructProject(full, top(ic[0], ic[2], f, g), opt.ColSet{}))
}
