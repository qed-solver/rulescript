package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPullOutVariantExtractions(t *testing.T) {
	rule := loadRule(t, "PullOutVariantExtractions")
	e := newEnv(t)
	s, sc := e.scan("t", 2)
	x := e.f.Metadata().AddColumn("x", types.Int)
	m := e.f.Metadata().AddColumn("m", types.Int)
	item := e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(sc[1]), e.num(1)), x)
	aggs := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(x)), m)}
	private := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(sc[0])}
	before := e.f.ConstructGroupBy(e.f.ConstructProject(s, memo.ProjectionsExpr{item}, opt.MakeColSet(sc[0], sc[1])), aggs, private)
	want := e.f.ConstructGroupBy(e.f.ConstructProject(s, memo.ProjectionsExpr{item}, opt.MakeColSet(sc[0])), aggs, private)
	e.expectRewrite(rule, before, want)

	plain := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(sc[1])), m)}
	e.expectNoMatch(rule, e.f.ConstructGroupBy(s, plain, private))
}
