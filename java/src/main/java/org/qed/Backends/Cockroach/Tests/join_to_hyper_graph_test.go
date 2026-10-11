package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinToHyperGraph(t *testing.T) {
	rule := loadRule(t, "JoinToHyperGraph")
	e := newEnv(t)
	a, ca := e.scan("t", 1)
	b, cb := e.scan("u", 1)
	c, cc := e.scan("nn", 1)

	ab := e.f.ConstructInnerJoin(a, b, e.filters(
		e.f.ConstructEq(e.v(ca[0]), e.v(cb[0])),
	), &memo.JoinPrivate{})
	topOn := e.filters(
		e.f.ConstructEq(e.v(ca[0]), e.v(cc[0])),
		e.f.ConstructLt(e.v(cb[0]), e.v(cc[0])),
	)
	before := e.f.ConstructInnerJoin(ab, c, topOn, &memo.JoinPrivate{})
	bc := e.f.ConstructInnerJoin(b, c, e.filters(
		e.f.ConstructLt(e.v(cb[0]), e.v(cc[0])),
	), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(a, bc, e.filters(
		e.f.ConstructEq(e.v(ca[0]), e.v(cb[0])),
		e.f.ConstructEq(e.v(ca[0]), e.v(cc[0])),
	), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(ab, c, topOn, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(a, c, e.filters(
		e.f.ConstructEq(e.v(ca[0]), e.v(cc[0])),
	), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(ab, c, e.filters(
		e.f.ConstructEq(e.v(ca[0]), e.v(cc[0])),
	), &memo.JoinPrivate{}))
}
