package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestInlineJoinConstantsRight(t *testing.T) {
	rule := loadRule(t, "InlineJoinConstantsRight")
	e := newEnv(t)
	left, lc := e.scan("u", 1)
	src, sc := e.scan("t", 1)
	cst, cc := e.scan("u", 1)
	rightJoin := e.f.ConstructInnerJoin(src, cst, e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(cc[0]))), &memo.JoinPrivate{})
	onBefore := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(sc[0])))
	onAfter := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(cc[0])))
	before := e.f.ConstructInnerJoin(left, rightJoin, onBefore, &memo.JoinPrivate{})
	after := e.f.ConstructInnerJoin(left, rightJoin, onAfter, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, after)
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(left, rightJoin, onBefore, &memo.JoinPrivate{}))
	ltJoin := e.f.ConstructInnerJoin(src, cst, e.filters(e.f.ConstructLt(e.v(sc[0]), e.v(cc[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(left, ltJoin, onBefore, &memo.JoinPrivate{}))
}
