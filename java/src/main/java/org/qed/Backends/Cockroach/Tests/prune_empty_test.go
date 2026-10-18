package rulescript_test

import (
	"testing"
)

func TestPruneEmpty(t *testing.T) {
	rule := loadRule(t, "PruneEmpty")
	e := newEnv(t)
	_, cols := e.scan("u", 1)
	empty := e.empty(cols...)
	before := e.f.ConstructSelect(empty, e.filters(e.f.ConstructEq(e.v(cols[0]), e.num(1))))
	want := e.empty(cols...)
	e.expectRewrite(rule, before, want)

	otherEmpty := e.empty(cols...)
	e.expectNoMatch(rule, otherEmpty)
}
