package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestRemoveLiteralFromGroupExpressions(t *testing.T) {
	rule := loadRule(t, "RemoveLiteralFromGroupExpressions")
	e := newEnv(t)
	s, sc := e.scan("t", 2)
	c := e.f.Metadata().AddColumn("c", types.Int)
	mx := e.f.Metadata().AddColumn("mx", types.Int)
	mn := e.f.Metadata().AddColumn("mn", types.Int)
	item := e.f.ConstructProjectionsItem(e.num(3), c)
	aggs := memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(sc[1])), mx),
		e.f.ConstructAggregationsItem(e.f.ConstructMin(e.v(sc[1])), mn),
	}
	keyed := e.f.ConstructProject(s, memo.ProjectionsExpr{item}, opt.MakeColSet(sc[0], sc[1]))
	before := e.f.ConstructGroupBy(keyed, aggs, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(sc[0], c)})
	grouped := e.f.ConstructGroupBy(s, aggs, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(sc[0])})
	want := e.f.ConstructProject(grouped, memo.ProjectionsExpr{item}, opt.MakeColSet(sc[0], mx, mn))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, grouped)
}
