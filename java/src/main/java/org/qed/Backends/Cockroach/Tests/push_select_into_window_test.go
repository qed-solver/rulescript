package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPushSelectIntoWindow(t *testing.T) {
	rule := loadRule(t, "PushSelectIntoWindow")
	e := newEnv(t)
	input, cols := e.scan("nn3", 2)
	partInput, partCols := e.scan("nn3", 2)
	w := e.f.Metadata().AddColumn("w", types.Int)
	part := e.f.ConstructGroupBy(partInput,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructConstAgg(e.v(partCols[1])), w)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(partCols[0])},
	)
	beforeJoin := e.f.ConstructInnerJoin(input, part,
		e.filters(e.f.ConstructEq(e.v(cols[0]), e.v(partCols[0]))), &memo.JoinPrivate{})
	before := e.f.ConstructSelect(beforeJoin, e.filters(
		e.f.ConstructEq(e.v(cols[0]), e.num(1)),
		e.f.ConstructEq(e.v(w), e.num(2)),
	))

	filtered := e.f.ConstructSelect(input, e.filters(e.f.ConstructEq(e.v(cols[0]), e.num(1))))
	wantPartInput, wantPartCols := e.scan("nn3", 2)
	wantPartInput = e.f.ConstructSelect(wantPartInput, e.filters(e.f.ConstructEq(e.v(wantPartCols[0]), e.num(1))))
	wantW := e.f.Metadata().AddColumn("want_w", types.Int)
	wantPart := e.f.ConstructGroupBy(wantPartInput,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructConstAgg(e.v(wantPartCols[1])), wantW)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(wantPartCols[0])},
	)
	wantJoin := e.f.ConstructInnerJoin(filtered, wantPart,
		e.filters(e.f.ConstructEq(e.v(cols[0]), e.v(wantPartCols[0]))), &memo.JoinPrivate{})
	want := e.f.ConstructSelect(wantJoin, e.filters(e.f.ConstructEq(e.v(wantW), e.num(2))))
	e.expectRewriteUpToColumnNames(rule, before, want)

	nearInput, nearCols := e.scan("nn3", 2)
	nearPartInput, nearPartCols := e.scan("nn3", 2)
	nearW := e.f.Metadata().AddColumn("near_w", types.Int)
	nearPart := e.f.ConstructGroupBy(nearPartInput,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructConstAgg(e.v(nearPartCols[1])), nearW)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(nearPartCols[0])},
	)
	nearJoin := e.f.ConstructInnerJoin(nearInput, nearPart,
		e.filters(e.f.ConstructEq(e.v(nearCols[0]), e.v(nearPartCols[0]))), &memo.JoinPrivate{})
	near := e.f.ConstructSelect(nearJoin, e.filters(
		e.f.ConstructEq(e.v(nearCols[1]), e.num(1)),
		e.f.ConstructEq(e.v(nearW), e.num(2)),
	))
	e.expectNoMatch(rule, near)
}
