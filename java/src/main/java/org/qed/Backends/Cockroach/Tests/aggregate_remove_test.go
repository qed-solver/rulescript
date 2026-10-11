package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateRemove(t *testing.T) {
	rule := loadRule(t, "AggregateRemove")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	private := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0])}
	before := e.f.ConstructGroupBy(s, nil, private)
	e.expectRewrite(rule, before, s)

	ts, tc := e.scan("t", 3)
	nonKey := e.f.ConstructProject(ts, nil, opt.MakeColSet(tc[1]))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(nonKey, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(tc[1]),
	}))

	aggCol := e.f.Metadata().AddColumn("count", types.Int)
	aggs := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(cols[0])), aggCol)}
	e.expectNoMatch(rule, e.f.ConstructGroupBy(s, aggs, private))

	e.expectNoMatch(rule, e.f.ConstructDistinctOn(s, nil, private))
}
