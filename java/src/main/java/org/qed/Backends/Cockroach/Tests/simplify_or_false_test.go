package rulescript_test

import "testing"

func TestSimplifyOrFalse(t *testing.T) {
	rule := loadRule(t, "SimplifyOrFalse")

	t.Run("equality predicate", func(t *testing.T) {
		e := newEnv(t)
		input, cols := e.scan("t", 3)
		predicate := e.f.ConstructEq(e.v(cols[1]), e.num(9))
		before := e.f.ConstructSelect(input, e.filters(e.f.ConstructOr(predicate, e.f.ConstructFalse())))
		want := e.f.ConstructSelect(input, e.filters(predicate))
		e.expectRewrite(rule, before, want)
	})

	t.Run("second predicate", func(t *testing.T) {
		e := newEnv(t)
		input, cols := e.scan("t", 3)
		predicate := e.f.ConstructEq(e.v(cols[2]), e.num(12))
		before := e.f.ConstructSelect(input, e.filters(e.f.ConstructOr(predicate, e.f.ConstructFalse())))
		want := e.f.ConstructSelect(input, e.filters(predicate))
		e.expectRewrite(rule, before, want)
	})

	t.Run("true near miss", func(t *testing.T) {
		e := newEnv(t)
		input, cols := e.scan("t", 3)
		predicate := e.f.ConstructEq(e.v(cols[1]), e.num(9))
		plan := e.f.ConstructSelect(input, e.filters(e.f.ConstructOr(predicate, e.f.ConstructTrue())))
		e.expectNoMatch(rule, plan)
	})
}
