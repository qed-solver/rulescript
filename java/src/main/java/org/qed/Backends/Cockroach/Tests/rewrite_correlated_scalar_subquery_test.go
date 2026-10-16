package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestRewriteCorrelatedScalarSubquery(t *testing.T) {
	rule := loadRule(t, "RewriteCorrelatedScalarSubquery")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	cnt := e.f.Metadata().AddColumn("cnt", types.Int)
	aggs := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(rc[0])), cnt)}
	grouping := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])}
	before := e.f.ConstructGroupBy(e.f.ConstructLeftJoinApply(l, r, on, &memo.JoinPrivate{}), aggs, grouping)
	want := e.f.ConstructGroupBy(e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}), aggs, grouping)
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, want)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(e.f.ConstructInnerJoinApply(l, r, on, &memo.JoinPrivate{}), aggs, grouping))
}
