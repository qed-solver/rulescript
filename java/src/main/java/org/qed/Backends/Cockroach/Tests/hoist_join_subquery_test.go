package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestHoistJoinSubquery(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "HoistJoinSubquery")
	left, lc := e.scan("u", 1)
	right, rc := e.scan("t", 1)
	sub, sc := e.scan("nn", 1)
	on := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	filteredSub := e.f.ConstructSelect(sub, e.filters(e.f.ConstructGt(e.v(sc[0]), e.num(0))))
	exists := e.exists(filteredSub)
	before := e.f.ConstructInnerJoin(left, right, e.filters(on, exists), &memo.JoinPrivate{})
	wantRight := e.f.ConstructSelect(right, e.filters(exists))
	want := e.f.ConstructInnerJoin(left, wantRight, e.filters(on), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(left, right, e.filters(on), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(left, right, e.filters(on, exists), &memo.JoinPrivate{}))
}

func TestSQLHoistJoinSubquery(t *testing.T) {
	expectSQLFires(t, "HoistJoinSubquery", "SELECT * FROM u JOIN t ON u.x=t.a AND EXISTS (SELECT * FROM (VALUES (1),(2)) AS s(a) WHERE a>0)")
}
