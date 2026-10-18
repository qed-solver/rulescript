package rulescript_test

import (
	"testing"
)

func TestFilterMerge(t *testing.T) {
	rule := loadRule(t, "FilterMerge")
	e := newEnv(t)
	s, sc := e.scan("t", 3)
	inner := e.f.ConstructEq(e.v(sc[0]), e.num(1))
	outer := e.f.ConstructEq(e.v(sc[1]), e.num(2))
	before := e.f.ConstructSelect(e.f.ConstructSelect(s, e.filters(inner)), e.filters(outer))
	e.expectRewrite(rule, before, e.f.ConstructSelect(s, e.filters(inner, outer)))

	innerSelect := e.f.ConstructSelect(s, e.filters(inner))
	project := e.f.ConstructProject(innerSelect, nil, innerSelect.Relational().OutputCols)
	e.expectNoMatch(rule, e.f.ConstructSelect(project, e.filters(outer)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(outer)))
}
