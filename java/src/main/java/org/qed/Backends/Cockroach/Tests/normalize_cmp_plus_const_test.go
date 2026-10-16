package rulescript_test

import "testing"

func TestNormalizeCmpPlusConst(t *testing.T) {
	rule := loadRule(t, "NormalizeCmpPlusConst")
	e := newEnv(t)
	s, cols := e.scan("t", 3)
	a, c1, c2 := e.v(cols[0]), e.v(cols[1]), e.v(cols[2])
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructLt(e.f.ConstructPlus(a, c1), c2)))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructLt(a, e.f.ConstructMinus(c2, c1))))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.f.ConstructPlus(a, c1), c2))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructLt(e.f.ConstructMinus(a, c1), c2))))
}
