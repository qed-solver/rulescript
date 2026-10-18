package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinToMultiJoin(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "JoinToMultiJoin")

	x, xc := e.scan("u", 1)
	y, yc := e.scan("u", 1)
	z, zc := e.scan("u", 1)
	yz := e.f.ConstructInnerJoin(y, z, e.filters(e.f.ConstructEq(e.v(yc[0]), e.v(zc[0]))), &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(x, yz, e.filters(e.f.ConstructEq(e.v(xc[0]), e.v(yc[0]))), &memo.JoinPrivate{})

	yzFree := e.f.ConstructInnerJoin(y, z, nil, &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(x, yzFree, e.filters(
		e.f.ConstructEq(e.v(yc[0]), e.v(zc[0])),
		e.f.ConstructEq(e.v(xc[0]), e.v(yc[0])),
	), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructLeftJoin(x, yz, e.filters(e.f.ConstructEq(e.v(xc[0]), e.v(yc[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
