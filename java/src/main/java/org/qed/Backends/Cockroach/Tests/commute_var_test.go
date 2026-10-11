package rulescript_test

import (
	"testing"
)

func TestCommuteVar(t *testing.T) {
	rule := loadRule(t, "CommuteVar")
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	computed := e.f.ConstructPlus(e.v(cols[0]), e.num(1))
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(computed, e.v(cols[0]))))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.v(cols[0]), computed)))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, want)
	wrongArg := e.f.ConstructPlus(e.v(cols[1]), e.num(1))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(wrongArg, e.v(cols[0])))))
}

func TestSQLCommuteVar(t *testing.T) {
	expectSQLRewrites(t, "CommuteVar", "SELECT x FROM u WHERE x + 1 = x")
}
