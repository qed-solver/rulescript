package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateMerge(t *testing.T) {
	rule := loadRule(t, "AggregateMerge")
	e := newEnv(t)
	s, sc := e.scan("t", 1)
	c0 := sc[0]
	k1 := e.f.Metadata().AddColumn("k1", types.Int)
	k2 := e.f.Metadata().AddColumn("k2", types.Int)
	v := e.f.Metadata().AddColumn("v", types.Int)
	keyed := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(c0), k1),
		e.f.ConstructProjectionsItem(e.v(c0), k2),
	}, opt.MakeColSet(c0))
	bottom := e.f.ConstructGroupBy(keyed, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructMin(e.v(c0)), v),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1, k2)})
	top := e.f.ConstructGroupBy(bottom, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1)})

	got, ok := rule.Apply(&e.f, top)
	if !ok {
		t.Fatalf("AggregateMerge did not apply to:\n%s", e.format(top))
	}
	g, isGroup := got.(*memo.GroupByExpr)
	if !isGroup || len(g.Aggregations) != 0 {
		t.Fatalf("expected a group-by without aggregations, got:\n%s", e.format(got))
	}
	p, isProject := g.Input.(*memo.ProjectExpr)
	if !isProject || len(p.Projections) != 1 || !p.Passthrough.Equals(opt.MakeColSet(c0)) {
		t.Fatalf("expected a single key projection over the scan, got:\n%s", e.format(got))
	}
	if !g.GroupingCols.Equals(opt.MakeColSet(p.Projections[0].Col)) {
		t.Fatalf("grouping columns %v do not match the key projection %v", g.GroupingCols, p.Projections[0].Col)
	}
	if _, isScan := p.Input.(*memo.ScanExpr); !isScan {
		t.Fatalf("expected the key projection to read the scan, got:\n%s", e.format(got))
	}

	want := e.f.ConstructGroupBy(
		e.f.ConstructProject(s, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.v(c0), k2),
		}, opt.MakeColSet(c0)),
		memo.AggregationsExpr{},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k2)},
	)
	e.expectRewriteUpToFreshColumns(rule, top, want)

	extra := e.f.Metadata().AddColumn("extra", types.Int)
	withAgg := e.f.ConstructGroupBy(bottom, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructMin(e.v(v)), extra),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1)})
	e.expectNoMatch(rule, withAgg)

	oneKey := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(c0), k1),
	}, opt.MakeColSet(c0))
	oneKeyBottom := e.f.ConstructGroupBy(oneKey, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructMin(e.v(c0)), v),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1)})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(oneKeyBottom, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1)}))

	e.expectNoMatch(rule, e.f.ConstructGroupBy(bottom, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k2)}))
}
