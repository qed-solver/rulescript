package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestRemoveRedundantAggregates(t *testing.T) {
	rule := loadRule(t, "RemoveRedundantAggregates")
	e := newEnv(t)
	input, cols := e.scan("t", 1)
	md := e.f.Metadata()
	k1 := md.AddColumn("k1", e.v(cols[0]).DataType())
	k2 := md.AddColumn("k2", e.v(cols[0]).DataType())
	v := md.AddColumn("v", e.v(cols[0]).DataType())
	keys := memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(cols[0]), k1),
		e.f.ConstructProjectionsItem(e.v(cols[0]), k2),
	}
	lowerInput := e.f.ConstructProject(input, keys, opt.MakeColSet(cols[0]))
	lower := e.f.ConstructGroupBy(lowerInput, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructSumInt(e.v(cols[0])), v),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1, k2)})
	before := e.f.ConstructGroupBy(lower, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1)})

	wantKey := k1
	wantInput := e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(cols[0]), wantKey),
	}, opt.ColSet{})
	want := e.f.ConstructGroupBy(wantInput, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(wantKey)})
	e.expectRewrite(rule, before, want)

	nearInput, nearCols := e.scan("t", 1)
	nearK1 := md.AddColumn("near_k1", e.v(nearCols[0]).DataType())
	nearK2 := md.AddColumn("near_k2", e.v(nearCols[0]).DataType())
	nearV := md.AddColumn("near_v", e.v(nearCols[0]).DataType())
	nearLowerInput := e.f.ConstructProject(nearInput, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(nearCols[0]), nearK1),
		e.f.ConstructProjectionsItem(e.v(nearCols[0]), nearK2),
	}, opt.MakeColSet(nearCols[0]))
	nearLower := e.f.ConstructGroupBy(nearLowerInput, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructSumInt(e.v(nearCols[0])), nearV),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(nearK1, nearK2)})
	nearMiss := e.f.ConstructGroupBy(nearLower, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(nearK2)})
	e.expectNoMatch(rule, nearMiss)
}
