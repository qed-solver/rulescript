package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSemiJoinJoinTranspose(t *testing.T) {
	rule := loadRule(t, "SemiJoinJoinTranspose")
	e := newEnv(t)
	x, xc := e.scan("t", 1)
	y, yc := e.scan("u", 1)
	z, zc := e.scan("nn", 1)

	c := e.filters(e.f.ConstructEq(e.v(xc[0]), e.v(yc[0])))
	s := e.filters(e.f.ConstructEq(e.v(xc[0]), e.v(zc[0])))

	inner := e.f.ConstructInnerJoin(x, y, c, &memo.JoinPrivate{})
	before := e.f.ConstructSemiJoin(inner, z, s, &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(
		e.f.ConstructSemiJoin(x, z, s, &memo.JoinPrivate{}),
		y, c, &memo.JoinPrivate{},
	)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructSemiJoin(
		e.f.ConstructLeftJoin(x, y, c, &memo.JoinPrivate{}), z, s, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(inner, z, s, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(x, z, s, &memo.JoinPrivate{}))
}
