package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateProjectPullUpConstants(t *testing.T) {
	rule := loadRule(t, "AggregateProjectPullUpConstants")
	e := newEnv(t)
	s, sc := e.scan("t", 3)
	constCol := e.f.Metadata().AddColumn("true", types.Bool)
	fcol := e.f.Metadata().AddColumn("f", types.Int)
	aggs := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(sc[0])), fcol)}
	trueItems := memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructTrue(), constCol)}
	input := e.f.ConstructProject(s, trueItems, opt.MakeColSet(sc[0]))

	before := e.f.ConstructGroupBy(input, aggs,
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(constCol, sc[0])})
	reduced := e.f.ConstructGroupBy(input, aggs,
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(sc[0])})
	want := e.f.ConstructProject(reduced, trueItems, opt.MakeColSet(sc[0], fcol))
	e.expectRewrite(rule, before, want)

	falseInput := e.f.ConstructProject(s,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructFalse(), constCol)},
		opt.MakeColSet(sc[0]))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(falseInput, aggs,
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(constCol, sc[0])}))

	e.expectNoMatch(rule, e.f.ConstructGroupBy(input, aggs,
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(sc[0])}))

	e.expectNoMatch(rule, e.f.ConstructScalarGroupBy(input, aggs, &memo.GroupingPrivate{}))

	e.expectNoMatch(rule, e.f.ConstructGroupBy(s, aggs,
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(sc[0])}))
}
