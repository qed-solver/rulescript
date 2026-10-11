package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPushSelectIntoGroupBy(t *testing.T) {
	rule := loadRule(t, "PushSelectIntoGroupBy")
	e := newEnv(t)
	s, cols := twoNonNullColumns(e)
	aggCol := e.f.Metadata().AddColumn("count", types.Int)
	aggs := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(cols[1])), aggCol)}
	private := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0])}
	grouped := e.f.ConstructGroupBy(s, aggs, private)
	p := e.f.ConstructEq(e.v(cols[0]), e.num(1))
	r := e.f.ConstructGt(e.v(cols[0]), e.num(0))
	before := e.f.ConstructSelect(grouped, e.filters(p, r))
	want := e.f.ConstructGroupBy(e.f.ConstructSelect(s, e.filters(p, r)), aggs, private)
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(grouped, e.filters(p)))
	e.expectNoMatch(rule, e.f.ConstructSelect(grouped, e.filters(p, e.f.ConstructGt(e.v(aggCol), e.num(0)))))
}

func TestSQLPushSelectIntoGroupBy(t *testing.T) {
	expectSQLRewrites(t, "PushSelectIntoGroupBy", "SELECT x, count(y) FROM (VALUES (1, 2)) AS v(x,y) GROUP BY x HAVING x>0 AND x<10")
}
