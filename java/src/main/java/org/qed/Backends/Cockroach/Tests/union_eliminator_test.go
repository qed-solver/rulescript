package rulescript_test

import "testing"

func TestUnionEliminator(t *testing.T) {
	rule := loadRule(t, "UnionEliminator")
	e := newEnv(t)
	s, c := e.scan("t", 3)

	e.expectNoMatch(rule, s)

	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(c[0]), e.num(1)))))
	union, _ := oneColumnUnionAll(e)
	e.expectNoMatch(rule, union)
	e.expectNoMatch(rule, e.empty(c[0]))
}
