package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateExistsGroupBy(t *testing.T) {
	rule := loadRule(t, "EliminateExistsGroupBy")
	e := newEnv(t)
	outer, _ := e.scan("u", 1)
	input, ic := e.scan("t", 1)
	key := e.f.Metadata().AddColumn("g", types.Int)
	keyed := e.f.ConstructProject(input, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(ic[0]), key)}, input.Relational().OutputCols)
	groupBy := e.f.ConstructGroupBy(keyed, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(key)})
	before := e.f.ConstructSemiJoin(outer, groupBy, e.filters(), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, e.f.ConstructSemiJoin(outer, input, e.filters(), &memo.JoinPrivate{}))

	e.expectNoMatch(rule, e.f.ConstructAntiJoin(outer, groupBy, e.filters(), &memo.JoinPrivate{}))
	countItem := e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(ic[0])), e.f.Metadata().AddColumn("count", types.Int))
	counted := e.f.ConstructGroupBy(keyed, memo.AggregationsExpr{countItem}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(key)})
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(outer, counted, e.filters(), &memo.JoinPrivate{}))
	scalar := e.f.ConstructScalarGroupBy(input, memo.AggregationsExpr{}, &memo.GroupingPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(outer, scalar, e.filters(), &memo.JoinPrivate{}))
}
