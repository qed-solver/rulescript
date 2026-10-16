package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateExpandWithinDistinct(t *testing.T) {
	rule := loadRule(t, "AggregateExpandWithinDistinct")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	cnt := e.f.Metadata().AddColumn("cnt", types.Int)
	distinctCount := memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructAggDistinct(e.f.ConstructCount(e.v(cols[0]))), cnt),
	}
	before := e.f.ConstructScalarGroupBy(s, distinctCount, &memo.GroupingPrivate{})
	dedup := e.f.ConstructGroupBy(s, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0])})
	want := e.f.ConstructScalarGroupBy(dedup, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(cols[0])), cnt),
	}, &memo.GroupingPrivate{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, want)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(s, distinctCount, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(cols[0]),
	}))
	wide, wideCols := e.scan("t", 2)
	e.expectNoMatch(rule, e.f.ConstructScalarGroupBy(wide, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructAggDistinct(e.f.ConstructCount(e.v(wideCols[0]))), cnt),
	}, &memo.GroupingPrivate{}))
}

func TestSQLAggregateExpandWithinDistinct(t *testing.T) {
	expectSQLFires(t, "AggregateExpandWithinDistinct", "SELECT count(DISTINCT x) FROM u")
}
