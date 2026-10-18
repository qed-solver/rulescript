package rulescript_test

import "testing"

func TestNormalizeNestedAnds(t *testing.T) {
	rule := loadRule(t, "NormalizeNestedAnds")
	e := newEnv(t)
	input, cols := e.scan("t", 3)
	p := e.f.ConstructEq(e.v(cols[0]), e.num(1))
	q := e.f.ConstructEq(e.v(cols[1]), e.num(2))
	r := e.f.ConstructEq(e.v(cols[2]), e.num(3))
	nested := e.f.ConstructAnd(p, e.f.ConstructAnd(q, r))
	before := e.f.ConstructSelect(input, e.filters(nested))
	want := e.f.ConstructSelect(input, e.filters(e.f.ConstructAnd(e.f.ConstructAnd(p, q), r)))
	t.Logf("factory input: %s", e.format(before))
	e.expectRewrite(rule, before, want)

	e2 := newEnv(t)
	input2, cols2 := e2.scan("t", 3)
	a := e2.f.ConstructEq(e2.v(cols2[0]), e2.num(1))
	b := e2.f.ConstructEq(e2.v(cols2[1]), e2.num(2))
	c := e2.f.ConstructEq(e2.v(cols2[2]), e2.num(3))
	nearMiss := e2.f.ConstructSelect(input2, e2.filters(e2.f.ConstructAnd(e2.f.ConstructAnd(a, b), c)))
	e2.expectNoMatch(rule, nearMiss)
}
