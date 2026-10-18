package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateUnionAggregate(t *testing.T) {
	rule := loadRule(t, "AggregateUnionAggregate")
	e := newEnv(t)

	a, ac := e.scan("u", 1)
	l, lc := e.scan("t", 1)

	innerBefore := e.f.ConstructGroupBy(l, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])})
	outOld := e.f.Metadata().AddColumn("union", types.Int)
	unionBefore := e.f.ConstructUnionAll(a, innerBefore, &memo.SetPrivate{
		LeftCols: opt.ColList{ac[0]}, RightCols: opt.ColList{lc[0]}, OutCols: opt.ColList{outOld},
	})
	before := e.f.ConstructGroupBy(unionBefore, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(outOld)})

	unionAfter := e.f.ConstructUnionAll(a, l, &memo.SetPrivate{
		LeftCols: opt.ColList{ac[0]}, RightCols: opt.ColList{lc[0]}, OutCols: opt.ColList{outOld},
	})
	want := e.f.ConstructGroupBy(unionAfter, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(outOld)})

	e.expectRewrite(rule, before, want)

	a3, ac3 := e.scan("u", 1)
	l3, lc3 := e.scan("t", 1)
	inner3 := e.f.ConstructGroupBy(l3, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc3[0])})
	out3 := e.f.Metadata().AddColumn("union", types.Int)
	distinctUnion := e.f.ConstructUnion(a3, inner3, &memo.SetPrivate{
		LeftCols: opt.ColList{ac3[0]}, RightCols: opt.ColList{lc3[0]}, OutCols: opt.ColList{out3},
	})
	notAll := e.f.ConstructGroupBy(distinctUnion, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(out3)})
	e.expectNoMatch(rule, notAll)

	a4, ac4 := e.scan("u", 1)
	l4, lc4 := e.scan("t", 1)
	out4 := e.f.Metadata().AddColumn("union", types.Int)
	noNesting := e.f.ConstructUnionAll(a4, l4, &memo.SetPrivate{
		LeftCols: opt.ColList{ac4[0]}, RightCols: opt.ColList{lc4[0]}, OutCols: opt.ColList{out4},
	})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(noNesting, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(out4)}))
}
