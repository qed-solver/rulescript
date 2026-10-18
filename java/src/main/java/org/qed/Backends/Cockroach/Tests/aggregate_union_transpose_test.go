package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateUnionTranspose(t *testing.T) {
	rule := loadRule(t, "AggregateUnionTranspose")
	e := newEnv(t)
	l, lc := e.scan("t", 2)
	r, rc := e.scan("u", 2)
	out := []opt.ColumnID{
		e.f.Metadata().AddColumn("o1", types.Int),
		e.f.Metadata().AddColumn("o2", types.Int),
	}
	union := e.f.ConstructUnionAll(l, r, &memo.SetPrivate{LeftCols: lc, RightCols: rc, OutCols: out})
	before := e.f.ConstructGroupBy(union, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(out...)})

	leftDedup := e.f.ConstructGroupBy(l, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc...)})
	rightDedup := e.f.ConstructGroupBy(r, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc...)})
	innerUnion := e.f.ConstructUnionAll(leftDedup, rightDedup, &memo.SetPrivate{LeftCols: lc, RightCols: rc, OutCols: out})
	want := e.f.ConstructGroupBy(innerUnion, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(out...)})
	e.expectRewriteUpToFreshColumns(rule, before, want)

	distinctUnion := e.f.ConstructUnion(l, r, &memo.SetPrivate{LeftCols: lc, RightCols: rc, OutCols: out})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(distinctUnion, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(out...)}))

	allUnion := e.f.ConstructUnionAll(l, r, &memo.SetPrivate{LeftCols: lc, RightCols: rc, OutCols: out})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(allUnion, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(out[:1]...)}))
}
