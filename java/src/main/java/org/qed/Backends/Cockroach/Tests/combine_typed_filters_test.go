package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestCombineTypedFilters(t *testing.T) {
	rule := loadRule(t, "CombineTypedFilters")
	e := newEnv(t)
	src, c := e.scan("t", 3)
	p1 := e.f.ConstructEq(e.v(c[0]), e.num(1))
	p2 := e.f.ConstructGt(e.v(c[1]), e.num(2))
	before := e.f.ConstructSelect(e.f.ConstructSelect(src, e.filters(p1)), e.filters(p2))
	e.expectRewrite(rule, before, e.f.ConstructSelect(src, e.filters(p1, p2)))
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(p1)))
	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructProject(src, memo.ProjectionsExpr{}, opt.MakeColSet(c[0])), e.filters(p2)))
}
