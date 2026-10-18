package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestRemoveRepetitionFromGroupExpressions(t *testing.T) {
	rule := loadRule(t, "RemoveRepetitionFromGroupExpressions")
	e := newEnv(t)
	scan, cols := e.scan("t", 3)
	keyA := e.f.Metadata().AddColumn("key_a", types.Int)
	keyB := e.f.Metadata().AddColumn("key_b", types.Int)
	duplicate := e.f.Metadata().AddColumn("duplicate", types.Int)
	value := e.f.Metadata().AddColumn("value", types.Int)
	projected := e.f.ConstructProject(scan, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(cols[0]), keyA),
		e.f.ConstructProjectionsItem(e.v(cols[1]), keyB),
		e.f.ConstructProjectionsItem(e.v(cols[0]), duplicate),
		e.f.ConstructProjectionsItem(e.v(cols[2]), value),
	}, opt.ColSet{})
	sumCol := e.f.Metadata().AddColumn("sum", types.Int)
	grouped := e.f.ConstructGroupBy(projected, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructSumInt(e.v(value)), sumCol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(keyA, keyB, duplicate)})
	outA := e.f.Metadata().AddColumn("out_a", types.Int)
	outB := e.f.Metadata().AddColumn("out_b", types.Int)
	outSum := e.f.Metadata().AddColumn("out_sum", types.Int)
	before := e.f.ConstructProject(grouped, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(keyA), outA),
		e.f.ConstructProjectionsItem(e.v(keyB), outB),
		e.f.ConstructProjectionsItem(e.v(sumCol), outSum),
	}, opt.ColSet{})
	want := e.f.ConstructProject(e.f.ConstructGroupBy(scan, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructSumInt(e.v(cols[2])), sumCol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0], cols[1])}), memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(cols[0]), outA),
		e.f.ConstructProjectionsItem(e.v(cols[1]), outB),
		e.f.ConstructProjectionsItem(e.v(sumCol), outSum),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	e2 := newEnv(t)
	scan2, cols2 := e2.scan("t", 3)
	keyA2 := e2.f.Metadata().AddColumn("key_a", types.Int)
	keyB2 := e2.f.Metadata().AddColumn("key_b", types.Int)
	duplicate2 := e2.f.Metadata().AddColumn("different", types.Int)
	value2 := e2.f.Metadata().AddColumn("value", types.Int)
	projected2 := e2.f.ConstructProject(scan2, memo.ProjectionsExpr{
		e2.f.ConstructProjectionsItem(e2.v(cols2[0]), keyA2),
		e2.f.ConstructProjectionsItem(e2.v(cols2[1]), keyB2),
		e2.f.ConstructProjectionsItem(e2.v(cols2[1]), duplicate2),
		e2.f.ConstructProjectionsItem(e2.v(cols2[2]), value2),
	}, opt.ColSet{})
	sumCol2 := e2.f.Metadata().AddColumn("sum", types.Int)
	grouped2 := e2.f.ConstructGroupBy(projected2, memo.AggregationsExpr{
		e2.f.ConstructAggregationsItem(e2.f.ConstructSumInt(e2.v(value2)), sumCol2),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(keyA2, keyB2, duplicate2)})
	nearMiss := e2.f.ConstructProject(grouped2, memo.ProjectionsExpr{
		e2.f.ConstructProjectionsItem(e2.v(keyA2), e2.f.Metadata().AddColumn("out_a", types.Int)),
		e2.f.ConstructProjectionsItem(e2.v(keyB2), e2.f.Metadata().AddColumn("out_b", types.Int)),
		e2.f.ConstructProjectionsItem(e2.v(sumCol2), e2.f.Metadata().AddColumn("out_sum", types.Int)),
	}, opt.ColSet{})
	e2.expectNoMatch(rule, nearMiss)
}
