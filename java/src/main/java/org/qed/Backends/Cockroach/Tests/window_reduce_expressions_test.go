package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestWindowReduceExpressions(t *testing.T) {
	rule := loadRule(t, "WindowReduceExpressions")
	e := newEnv(t)
	input, ic := e.scan("t", 2)
	operand, oc := e.scan("t", 2)
	flag := e.f.Metadata().AddColumn("flag", types.Bool)
	cnt := e.f.Metadata().AddColumn("cnt", types.Int)
	window := func(value opt.ScalarExpr) memo.RelExpr {
		computed := e.f.ConstructProject(operand, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(value, flag),
		}, opt.MakeColSet(oc[0]))
		perPartition := e.f.ConstructGroupBy(computed, memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(flag)), cnt),
		}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(oc[0])})
		return e.f.ConstructInnerJoin(input, perPartition,
			e.filters(e.f.ConstructEq(e.v(ic[0]), e.v(oc[0]))), &memo.JoinPrivate{})
	}
	positive := e.f.ConstructGt(e.v(oc[1]), e.num(0))
	e.expectRewrite(rule, window(e.f.ConstructAnd(e.f.ConstructFalse(), positive)), window(e.f.ConstructFalse()))
	e.expectNoMatch(rule, window(e.f.ConstructAnd(e.f.ConstructTrue(), positive)))
}
