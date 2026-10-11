package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestHoistUnboundJoinFilterFromExistsSubquery(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "HoistUnboundJoinFilterFromExistsSubquery")
	outer, _ := e.scan("nn", 1)
	left, lc := e.scan("u", 1)
	right, rc := e.scan("t", 1)
	global := e.f.ConstructEq(e.num(1), e.num(1))
	other := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	beforeJoin := e.f.ConstructInnerJoin(left, right, e.filters(global, other), &memo.JoinPrivate{})
	afterJoin := e.f.ConstructInnerJoin(left, right, e.filters(other), &memo.JoinPrivate{})
	before := e.f.ConstructSelect(outer, e.filters(e.exists(beforeJoin)))
	want := e.f.ConstructSelect(outer, e.filters(e.exists(afterJoin), global))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(e.exists(afterJoin))))
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(e.exists(
		e.f.ConstructInnerJoin(left, right, e.filters(other, global), &memo.JoinPrivate{})))))
}

func TestSQLHoistUnboundJoinFilterFromExistsSubquery(t *testing.T) {
	expectSQLFires(t, "HoistUnboundJoinFilterFromExistsSubquery", "SELECT a FROM nn WHERE EXISTS (SELECT * FROM (VALUES (1)) AS l(x) JOIN (VALUES (1)) AS r(y) ON random()>0 AND x=y)")
}
