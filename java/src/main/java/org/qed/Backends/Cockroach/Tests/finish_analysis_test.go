package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFinishAnalysis(t *testing.T) {
	rule := loadRule(t, "FinishAnalysis")
	e := newEnv(t)
	s, sc := e.scan("t", 2)
	g := e.f.Metadata().AddColumn("g", types.Int)
	m := e.f.Metadata().AddColumn("m", types.Int)
	item := e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(sc[0]), e.num(1)), g)
	aggs := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(sc[1])), m)}
	private := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(g)}
	before := e.f.ConstructGroupBy(e.f.ConstructProject(s, memo.ProjectionsExpr{item}, opt.MakeColSet(sc[0], sc[1])), aggs, private)
	want := e.f.ConstructGroupBy(e.f.ConstructProject(s, memo.ProjectionsExpr{item}, opt.MakeColSet(sc[1])), aggs, private)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructGroupBy(s, aggs, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(sc[0])}))
	one, _ := e.scan("u", 1)
	e.expectNoMatch(rule, e.f.ConstructScalarGroupBy(one, aggs, &memo.GroupingPrivate{}))
}
