package rulescript_test

import (
	"testing"
)

func TestHoistUnboundFilterFromExistsSubquery(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "HoistUnboundFilterFromExistsSubquery")
	outer, _ := e.scan("u", 1)
	inner, ic := e.scan("t", 1)
	global := e.f.ConstructEq(e.num(1), e.num(1))
	other := e.f.ConstructGt(e.v(ic[0]), e.num(0))
	beforeBody := e.f.ConstructSelect(inner, e.filters(global, other))
	afterBody := e.f.ConstructSelect(inner, e.filters(other))
	before := e.f.ConstructSelect(outer, e.filters(e.exists(beforeBody)))
	want := e.f.ConstructSelect(outer, e.filters(e.exists(afterBody), global))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(e.exists(afterBody))))
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(e.exists(
		e.f.ConstructSelect(inner, e.filters(other, global))))))
}

func TestSQLHoistUnboundFilterFromExistsSubquery(t *testing.T) {
	expectSQLFires(t, "HoistUnboundFilterFromExistsSubquery", "SELECT x FROM u WHERE EXISTS (SELECT * FROM (VALUES (1),(2)) AS s(a) WHERE random()>0 AND a>0)")
}
