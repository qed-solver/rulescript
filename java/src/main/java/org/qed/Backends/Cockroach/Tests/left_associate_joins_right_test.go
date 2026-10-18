package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestLeftAssociateJoinsRight(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "LeftAssociateJoinsRight")
	a, ac := e.scan("u", 1)
	b, bc := e.scan("t", 1)
	c, cc := e.scan("nn", 1)

	pax := e.f.ConstructIsNot(e.v(ac[0]), memo.NullSingleton)
	pbu := e.f.ConstructEq(e.v(bc[0]), e.v(cc[0]))
	beforeLeft := e.f.ConstructInnerJoin(a, b, nil, &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(beforeLeft, c, e.filters(pax, pbu), &memo.JoinPrivate{})
	bcJoin := e.f.ConstructInnerJoin(b, c, e.filters(e.f.ConstructEq(e.v(bc[0]), e.v(cc[0]))), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(a, bcJoin, e.filters(e.f.ConstructIsNot(e.v(ac[0]), memo.NullSingleton)), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructInnerJoin(beforeLeft, c, e.filters(pax), &memo.JoinPrivate{})
	e.expectRewrite(rule, nearMiss, e.f.ConstructInnerJoin(a, e.f.ConstructInnerJoin(b, c, nil, &memo.JoinPrivate{}), e.filters(pax), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(beforeLeft, c, e.filters(pax, pbu), &memo.JoinPrivate{}))
}
