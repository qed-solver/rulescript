package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestConstantPropagation(t *testing.T) {
	rule := loadRule(t, "ConstantPropagation")
	e := newEnv(t)
	source, sc := e.scan("t", 1)
	constRel, cc := e.scan("u", 1)
	on := e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(cc[0])))
	join := e.f.ConstructInnerJoin(source, constRel, on, &memo.JoinPrivate{})
	before := e.f.ConstructSelect(join, e.filters(
		e.f.ConstructEq(e.v(sc[0]), e.v(cc[0])),
		e.f.ConstructGt(e.v(sc[0]), e.num(0)),
	))
	want := e.f.ConstructSelect(join, e.filters(
		e.f.ConstructEq(e.v(sc[0]), e.v(cc[0])),
		e.f.ConstructGt(e.v(cc[0]), e.num(0)),
	))
	e.expectRewrite(rule, before, want)
	leftJoin := e.f.ConstructLeftJoin(source, constRel, on, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(leftJoin, e.filters(
		e.f.ConstructEq(e.v(sc[0]), e.v(cc[0])),
		e.f.ConstructGt(e.v(sc[0]), e.num(0)),
	)))
	e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(
		e.f.ConstructGt(e.v(sc[0]), e.num(0)),
		e.f.ConstructGt(e.v(sc[0]), e.num(1)),
	)))
}
