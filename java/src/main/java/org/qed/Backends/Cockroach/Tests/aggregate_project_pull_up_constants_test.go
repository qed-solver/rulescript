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
	s, sc := e.scan("t", 1)
	tc := e.f.Metadata().AddColumn("tc", types.Bool)
	ac := e.f.Metadata().AddColumn("f", types.Int)
	input := e.f.ConstructProject(
		s,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructTrue(), tc)},
		opt.MakeColSet(sc[0]),
	)
	before := e.f.ConstructGroupBy(
		input,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(sc[0])), ac)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(tc, sc[0])},
	)
	inner := e.f.ConstructGroupBy(
		input,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(sc[0])), ac)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(sc[0])},
	)
	want := e.f.ConstructProject(
		inner,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructTrue(), tc)},
		opt.MakeColSet(sc[0], ac),
	)
	e.expectRewriteUpToFreshColumns(rule, before, want)

	got, ok := rule.Apply(&e.f, before)
	if !ok {
		t.Fatalf("%s did not apply", rule.Name)
	}
	p, ok := got.(*memo.ProjectExpr)
	if !ok {
		t.Fatalf("result is not a project:\n%s", e.format(got))
	}
	g, ok := p.Input.(*memo.GroupByExpr)
	if !ok {
		t.Fatalf("project input is not a group by:\n%s", e.format(got))
	}
	if !g.GroupingCols.Equals(opt.MakeColSet(sc[0])) {
		t.Fatalf("grouping cols = %v, want %v", g.GroupingCols, opt.MakeColSet(sc[0]))
	}

	falseTc := e.f.Metadata().AddColumn("fc", types.Bool)
	falseInput := e.f.ConstructProject(
		s,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructFalse(), falseTc)},
		opt.MakeColSet(sc[0]),
	)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(
		falseInput,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(sc[0])), ac)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(falseTc, sc[0])},
	))

	filtered := e.f.ConstructSelect(input, e.filters(e.f.ConstructEq(e.v(sc[0]), e.num(1))))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(
		filtered,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(sc[0])), ac)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(tc, sc[0])},
	))
}
