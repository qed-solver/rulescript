package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinAssociate(t *testing.T) {
	rule := loadRule(t, "JoinAssociate")
	e := newEnv(t)
	a, ac := e.scan("t", 1)
	b, bc := e.scan("u", 1)
	c, cc := e.scan("nn", 1)

	pab := e.f.ConstructEq(e.v(ac[0]), e.v(bc[0]))
	pb := e.f.ConstructGt(e.v(bc[0]), e.num(1))
	pabc := e.f.ConstructLt(e.v(ac[0]), e.v(cc[0]))
	pbc := e.f.ConstructLt(e.v(bc[0]), e.v(cc[0]))
	pc := e.f.ConstructGt(e.v(cc[0]), e.num(2))

	ab := e.f.ConstructInnerJoin(a, b, e.filters(pab, pb), &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(ab, c, e.filters(pabc, pbc, pc), &memo.JoinPrivate{})
	bcJoin := e.f.ConstructInnerJoin(b, c, e.filters(pb, pbc, pc), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(a, bcJoin, e.filters(pab, pabc), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(ab, c, e.filters(pabc, pbc, pc), &memo.JoinPrivate{}))

	abNoPb := e.f.ConstructInnerJoin(a, b, e.filters(pab), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(abNoPb, c, e.filters(pabc, pbc, pc), &memo.JoinPrivate{}))

	pbOnA := e.f.ConstructGt(e.v(ac[0]), e.num(1))
	abBadPb := e.f.ConstructInnerJoin(a, b, e.filters(pab, pbOnA), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(abBadPb, c, e.filters(pabc, pbc, pc), &memo.JoinPrivate{}))

	e.expectNoMatch(rule, e.f.ConstructInnerJoin(a, c, e.filters(pabc, pbc, pc), &memo.JoinPrivate{}))
}
