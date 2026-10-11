package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPushSelectIntoUnorderedDistinctOn(t *testing.T) {
	rule := loadRule(t, "PushSelectIntoUnorderedDistinctOn")
	e := newEnv(t)
	s, cols := twoNonNullColumns(e)
	private := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols...)}
	grouped := e.f.ConstructGroupBy(s, nil, private)
	p := e.f.ConstructGt(e.v(cols[0]), e.num(0))
	r := e.f.ConstructLt(e.v(cols[1]), e.num(10))
	before := e.f.ConstructSelect(grouped, e.filters(p, r))
	want := e.f.ConstructGroupBy(e.f.ConstructSelect(s, e.filters(p, r)), nil, private)
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(grouped, e.filters(p)))
	aggCol := e.f.Metadata().AddColumn("count", types.Int)
	withAgg := e.f.ConstructGroupBy(s, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(cols[0])), aggCol),
	}, private)
	e.expectNoMatch(rule, e.f.ConstructSelect(withAgg, e.filters(p, r)))
}

func TestSQLPushSelectIntoUnorderedDistinctOn(t *testing.T) {
	expectSQLRewrites(t, "PushSelectIntoUnorderedDistinctOn", "SELECT a,b FROM (VALUES (1, 2)) AS v(a,b) GROUP BY a,b HAVING a>0 AND b<10")
}
