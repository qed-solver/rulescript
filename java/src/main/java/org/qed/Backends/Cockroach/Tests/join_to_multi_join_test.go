package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinToMultiJoin(t *testing.T) {
	rule := loadRule(t, "JoinToMultiJoin")
	e := newEnv(t)
	x, xc := e.scan("t", 1)
	y, yc := e.scan("u", 1)
	z, zc := e.scan("nn", 1)

	fm := e.f.ConstructLt(e.v(yc[0]), e.v(zc[0]))
	c := e.f.ConstructGt(e.v(xc[0]), e.v(zc[0]))
	yz := e.f.ConstructInnerJoin(y, z, e.filters(fm), &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(x, yz, e.filters(c), &memo.JoinPrivate{})
	yzFree := e.f.ConstructInnerJoin(y, z, nil, &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(x, yzFree, e.filters(fm, c), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(x, yz, e.filters(c), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(x,
		e.f.ConstructLeftJoin(y, z, e.filters(fm), &memo.JoinPrivate{}),
		e.filters(c), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructSelect(before, e.filters(c)))
}
