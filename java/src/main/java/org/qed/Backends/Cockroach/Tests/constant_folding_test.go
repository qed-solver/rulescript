package rulescript_test

import (
	"testing"
)

func TestConstantFolding(t *testing.T) {
	rule := loadRule(t, "ConstantFolding")
	e := newEnv(t)
	src, _ := e.scan("t", 3)
	cond := e.f.ConstructAnd(e.f.ConstructTrue(), e.f.ConstructFalse())
	before := e.f.ConstructSelect(src, e.filters(cond))
	e.expectRewrite(rule, before, e.f.ConstructSelect(src, e.filters(e.f.ConstructFalse())))
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructAnd(e.f.ConstructTrue(), e.f.ConstructTrue()))))
	e.expectRewrite(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructAnd(e.f.ConstructFalse(), e.f.ConstructTrue()))), e.f.ConstructSelect(src, e.filters(e.f.ConstructFalse())))
}
