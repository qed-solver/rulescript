package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinAggregateTranspose(t *testing.T) {
	rule := loadRule(t, "JoinAggregateTranspose")
	e := newEnv(t)
	left, lc := e.scan("u", 2)
	right, rc := e.scan("t", 1)
	aggCol := e.f.Metadata().AddColumn("sum", e.v(lc[1]).DataType())
	group := e.f.ConstructGroupBy(left, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructSumInt(e.v(lc[1])), aggCol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])})
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	before := e.f.ConstructInnerJoin(group, right, on, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, expectedJoinAggregateTranspose(e, left, right, lc, rc, aggCol))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(group, right, on, &memo.JoinPrivate{}))
}

func expectedJoinAggregateTranspose(e *env, left, right memo.RelExpr, lc, rc []opt.ColumnID, aggCol opt.ColumnID) memo.RelExpr {
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	joined := e.f.ConstructInnerJoin(left, right, on, &memo.JoinPrivate{})
	aggregated := e.f.ConstructGroupBy(joined, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructSumInt(e.v(lc[1])), aggCol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0], rc[0])})
	return e.f.ConstructProject(aggregated, nil, aggregated.Relational().OutputCols)
}
