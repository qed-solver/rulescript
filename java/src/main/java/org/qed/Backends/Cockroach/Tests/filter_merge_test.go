package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
)

func TestFilterMerge(t *testing.T) {
	rule := loadRule(t, "FilterMerge")
	e := newEnv(t)
	s, c := e.scan("t", 3)

	inner := e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(c[0]), e.num(1))))
	before := e.f.ConstructSelect(inner, e.filters(e.f.ConstructLt(e.v(c[1]), e.num(5))))
	want := e.f.ConstructSelect(s, e.filters(
		e.f.ConstructGt(e.v(c[0]), e.num(1)),
		e.f.ConstructLt(e.v(c[1]), e.num(5)),
	))
	e.expectRewrite(rule, before, want)

	innerEq := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.v(c[2]), e.num(7))))
	beforeNe := e.f.ConstructSelect(innerEq, e.filters(e.f.ConstructNe(e.v(c[0]), e.num(3))))
	wantNe := e.f.ConstructSelect(s, e.filters(
		e.f.ConstructEq(e.v(c[2]), e.num(7)),
		e.f.ConstructNe(e.v(c[0]), e.num(3)),
	))
	e.expectRewrite(rule, beforeNe, wantNe)

	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructLt(e.v(c[1]), e.num(5)))))
	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructProject(inner, nil, opt.MakeColSet(c[0], c[1])),
		e.filters(e.f.ConstructLt(e.v(c[1]), e.num(5))),
	))
	e.expectNoMatch(rule, e.f.ConstructProject(before, nil, opt.MakeColSet(c[0], c[1])))
}
