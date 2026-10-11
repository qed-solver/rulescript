package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestTryDecorrelateWindow(t *testing.T) {
	rule := loadRule(t, "TryDecorrelateWindow")
	e := newEnv(t)
	l, lc := e.scan("nn", 1)
	i1, ic := e.scan("nn3", 1)
	i2, i2c := e.scan("nn3", 1)
	wcol := e.f.Metadata().AddColumn("w", types.Int)
	scalar := e.f.ConstructScalarGroupBy(i2, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(i2c[0])), wcol),
	}, &memo.GroupingPrivate{})
	window := e.f.ConstructInnerJoin(i1, scalar, nil, &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(l, window,
		e.filters(e.f.ConstructGt(e.v(lc[0]), e.v(ic[0]))), &memo.JoinPrivate{})

	w := newEnv(t)
	wl, wlc := w.scan("nn", 1)
	wi1, wi1c := w.scan("nn3", 1)
	wi2, wi2c := w.scan("nn3", 1)
	ww := w.f.Metadata().AddColumn("w", types.Int)
	duplicate := w.f.Metadata().AddColumn("duplicate", types.Int)
	copyLeft := w.f.ConstructProject(wl, memo.ProjectionsExpr{
		w.f.ConstructProjectionsItem(w.v(wlc[0]), duplicate),
	}, opt.ColSet{})
	leftCross := w.f.ConstructInnerJoin(wl, wi1, nil, &memo.JoinPrivate{})
	rightCross := w.f.ConstructInnerJoin(copyLeft, wi2, nil, &memo.JoinPrivate{})
	grouped := w.f.ConstructGroupBy(rightCross, memo.AggregationsExpr{
		w.f.ConstructAggregationsItem(w.f.ConstructCount(w.v(wi2c[0])), ww),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(duplicate)})
	joined := w.f.ConstructInnerJoin(leftCross, grouped,
		w.filters(w.f.ConstructEq(w.v(wlc[0]), w.v(duplicate))), &memo.JoinPrivate{})
	selected := w.f.ConstructSelect(joined, w.filters(w.f.ConstructGt(w.v(wlc[0]), w.v(wi1c[0]))))
	want := w.f.ConstructProject(selected, nil, opt.MakeColSet(wlc[0], wi1c[0], ww))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, window,
		e.filters(e.f.ConstructGt(e.v(lc[0]), e.v(ic[0]))), &memo.JoinPrivate{}))
	groupedInput := e.f.ConstructGroupBy(i2, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(i2c[0])), wcol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(i2c[0])})
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l,
		e.f.ConstructInnerJoin(i1, groupedInput, nil, &memo.JoinPrivate{}),
		e.filters(e.f.ConstructGt(e.v(lc[0]), e.v(ic[0]))), &memo.JoinPrivate{}))
}

func TestSQLTryDecorrelateWindow(t *testing.T) {
	expectSQLFiresWith(t, "TryDecorrelateWindow",
		"SELECT * FROM (SELECT x FROM u) l JOIN (SELECT n.a, g.w FROM (SELECT a FROM nn) n CROSS JOIN (SELECT max(a) AS w FROM nn) g) q ON l.x = q.a",
		sqlHelpers{names: []string{"PruneScanCols", "EliminateProject"}})
}
