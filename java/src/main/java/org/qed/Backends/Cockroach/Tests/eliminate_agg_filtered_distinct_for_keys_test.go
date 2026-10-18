package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func sumOfColumn(e *env, c opt.ColumnID) opt.ScalarExpr {
	return e.f.ConstructSum(e.v(c).(*memo.VariableExpr))
}

func groupByKeyWithAgg(e *env, in memo.RelExpr, key opt.ColumnID, agg opt.ScalarExpr, out opt.ColumnID) memo.RelExpr {
	return e.f.ConstructGroupBy(
		in,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(agg, out)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(key)},
	)
}

func TestEliminateAggFilteredDistinctForKeys(t *testing.T) {
	rule := loadRule(t, "EliminateAggFilteredDistinctForKeys")
	e := newEnv(t)

	in, cols := e.scan("t", 1)
	out := e.f.Metadata().AddColumn("s", types.Int)
	before := groupByKeyWithAgg(e, in, cols[0], e.f.ConstructAggDistinct(sumOfColumn(e, cols[0])), out)
	want := groupByKeyWithAgg(e, in, cols[0], sumOfColumn(e, cols[0]), out)
	e.expectRewrite(rule, before, want)

	in3, cols3 := e.scan("t", 3)
	out3 := e.f.Metadata().AddColumn("s", types.Int)
	e.expectNoMatch(rule, groupByKeyWithAgg(e, in3, cols3[0], e.f.ConstructAggDistinct(sumOfColumn(e, cols3[2])), out3))

	in2, cols2 := e.scan("t", 2)
	out2 := e.f.Metadata().AddColumn("s", types.Int)
	e.expectNoMatch(rule, groupByKeyWithAgg(e, in2, cols2[1], e.f.ConstructAggDistinct(sumOfColumn(e, cols2[1])), out2))
}
