package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
)

func TestCalcRemove(t *testing.T) {
	rule := loadRule(t, "CalcRemove")
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	identity := e.f.ConstructProject(s, nil, opt.MakeColSet(cols...))
	e.expectRewrite(rule, identity, s)

	e.expectNoMatch(rule, e.f.ConstructProject(s, nil, opt.MakeColSet(cols[0])))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(cols[0]), e.num(0)))))
	e.expectNoMatch(rule, s)
}
