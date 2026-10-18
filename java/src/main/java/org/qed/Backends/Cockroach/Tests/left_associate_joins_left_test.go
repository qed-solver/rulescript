package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestLeftAssociateJoinsLeft(t *testing.T) {
	rule := loadRule(t, "LeftAssociateJoinsLeft")
	e := newEnv(t)
	a, acols := e.scan("u", 1)
	b, bcols := e.scan("t", 1)
	c, ccols := e.scan("u", 1)
	aCol, bCol, cCol := acols[0], bcols[0], ccols[0]
	ab := e.f.ConstructInnerJoin(a, b, nil, &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(ab, c, e.filters(
		e.f.ConstructEq(e.v(aCol), e.v(cCol)),
		e.f.ConstructEq(e.v(bCol), e.v(cCol)),
	), &memo.JoinPrivate{})
	ac := e.f.ConstructInnerJoin(a, c, e.filters(e.f.ConstructEq(e.v(aCol), e.v(cCol))), &memo.JoinPrivate{})
	bcac := e.f.ConstructInnerJoin(b, ac, e.filters(e.f.ConstructEq(e.v(bCol), e.v(cCol))), &memo.JoinPrivate{})
	want := e.f.ConstructProject(bcac, nil, opt.MakeColSet(aCol, bCol, cCol))
	e.expectRewrite(rule, before, want)

	innerWithFilter := e.f.ConstructInnerJoin(a, b, e.filters(e.f.ConstructEq(e.v(aCol), e.v(bCol))), &memo.JoinPrivate{})
	nearMiss := e.f.ConstructInnerJoin(innerWithFilter, c, e.filters(
		e.f.ConstructEq(e.v(aCol), e.v(cCol)),
		e.f.ConstructEq(e.v(bCol), e.v(cCol)),
	), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
