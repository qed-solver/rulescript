package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateProjectMerge(t *testing.T) {
	rule := loadRule(t, "AggregateProjectMerge")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	join := e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{})

	swapped := e.f.ConstructProject(join, memo.ProjectionsExpr{}, opt.MakeColSet(lc[0], rc[0]))
	aggOut := e.f.Metadata().AddColumn("f", types.Int)
	before := e.f.ConstructGroupBy(
		swapped,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(rc[0])), aggOut)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0])},
	)

	wantAgg := e.f.Metadata().AddColumn("f", types.Int)
	want := e.f.ConstructGroupBy(
		join,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(rc[0])), wantAgg)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0])},
	)
	e.expectRewriteUpToFreshColumns(rule, before, want)

	got, ok := rule.Apply(&e.f, before)
	if !ok {
		t.Fatalf("AggregateProjectMerge did not apply")
	}
	grouped, isGroupBy := got.(*memo.GroupByExpr)
	if !isGroupBy {
		t.Fatalf("expected a GroupBy result, got %s", e.format(got))
	}
	if !grouped.Private().(*memo.GroupingPrivate).GroupingCols.Equals(opt.MakeColSet(rc[0])) {
		t.Fatalf("expected grouping on the B column, got %s", e.format(got))
	}

	leftJoin := e.f.ConstructLeftJoin(l, r, nil, &memo.JoinPrivate{})
	leftProject := e.f.ConstructProject(leftJoin, memo.ProjectionsExpr{}, opt.MakeColSet(lc[0], rc[0]))
	leftAgg := e.f.Metadata().AddColumn("f", types.Int)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(
		leftProject,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(rc[0])), leftAgg)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0])},
	))

	otherArgAgg := e.f.Metadata().AddColumn("f", types.Int)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(
		swapped,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(lc[0])), otherArgAgg)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0])},
	))
}
