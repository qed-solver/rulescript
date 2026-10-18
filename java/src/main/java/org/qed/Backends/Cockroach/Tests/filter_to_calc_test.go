package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
)

func TestFilterToCalc(t *testing.T) {
	rule := loadRule(t, "FilterToCalc")
	e := newEnv(t)
	s, sc := e.scan("t", 1)
	cond := e.f.ConstructEq(e.v(sc[0]), e.num(1))
	before := e.f.ConstructSelect(s, e.filters(cond))
	after := e.f.ConstructProject(e.f.ConstructSelect(s, e.filters(cond)), nil, opt.MakeColSet(sc[0]))
	e.expectRewrite(rule, before, after)
	e.expectNoMatch(rule, e.f.ConstructProject(s, nil, opt.MakeColSet(sc[0])))

}
