package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestExtractGroupingPythonUDFFromAggregate(t *testing.T) {
	rule := loadRule(t, "ExtractGroupingPythonUDFFromAggregate")
	e := newEnv(t)
	s, sc := e.scan("t", 1)
	g := e.f.Metadata().AddColumn("g", types.Int)
	m := e.f.Metadata().AddColumn("m", types.Int)
	keyed := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(sc[0]), e.num(1)), g),
	}, opt.MakeColSet(sc[0]))
	aggs := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(sc[0])), m)}
	before := e.f.ConstructGroupBy(keyed, aggs, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(g)})

	e.expectNoMatch(rule, before)

	e.expectNoMatch(rule, e.f.ConstructGroupBy(s, aggs, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(sc[0])}))
	e.expectNoMatch(rule, e.f.ConstructScalarGroupBy(s, aggs, &memo.GroupingPrivate{}))
}
