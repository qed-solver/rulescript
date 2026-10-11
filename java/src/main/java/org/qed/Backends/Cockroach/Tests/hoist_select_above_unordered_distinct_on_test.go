package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestHoistSelectAboveUnorderedDistinctOn(t *testing.T) {
	rule := loadRule(t, "HoistSelectAboveUnorderedDistinctOn")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	input := e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{})
	cond := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	grouping := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0], rc[0])}
	before := e.f.ConstructGroupBy(e.f.ConstructSelect(input, cond), nil, grouping)
	want := e.f.ConstructSelect(e.f.ConstructGroupBy(input, nil, grouping), cond)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructGroupBy(input, nil, grouping))
	nullable, nullableCols := e.scan("u", 2)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(
		e.f.ConstructSelect(nullable, e.filters(e.f.ConstructEq(e.v(nullableCols[0]), e.num(1)))),
		nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(nullableCols...)}))
}

func TestSQLHoistSelectAboveUnorderedDistinctOn(t *testing.T) {
	expectSQLScanConstraint(t, "HoistSelectAboveUnorderedDistinctOn", "SELECT a, b FROM (VALUES (1, 2), (3, 4)) AS v(a, b) WHERE a > 0 GROUP BY a, b", "group-by", "")
}
